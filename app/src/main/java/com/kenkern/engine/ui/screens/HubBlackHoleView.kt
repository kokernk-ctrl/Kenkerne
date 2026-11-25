package com.kenkern.engine.ui.screens

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.kenkern.engine.diag.KenkernLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Fundo de buraco negro em tempo real do Hub (C1):
 *  - Renderiza a 50% da resolução em FBO offscreen e amplia por filtro linear;
 *  - 30 FPS controlados por RENDERMODE_WHEN_DIRTY (chamado pelo HomeScreen);
 *  - Pausa quando fora do Hub (`onPause()`) e aceita frame estático quando
 *    "reduzir animações" está ligado;
 *  - ZERO alocação por frame no thread GL.
 */
class HubBlackHoleView(
    context: Context,
    tempoInicial: Float = 0f,
    qualidadeInicial: QualidadeFundoHub = QualidadeFundoHub.ALTA
) : GLSurfaceView(context) {

    private val renderer = BlackHoleRenderer(tempoInicial, qualidadeInicial)

    init {
        try {
            setEGLContextClientVersion(3)
            preserveEGLContextOnPause = false
            setRenderer(renderer)
            renderMode = RENDERMODE_WHEN_DIRTY
        } catch (t: Throwable) {
            KenkernLog.e(TAG, "falha ao inicializar GLSurfaceView do buraco negro no Hub", t)
        }
    }

    fun atualizarEstado(tempoSegundos: Float, qualidade: QualidadeFundoHub) {
        renderer.tempoSegundos = tempoSegundos
        renderer.qualidade = qualidade
        requestRender()
    }

    private class BlackHoleRenderer(
        @Volatile var tempoSegundos: Float,
        @Volatile var qualidade: QualidadeFundoHub
    ) : Renderer {

        // Quad tela cheia (-1..1 xy, 0..1 uv) alocado UMA única vez
        private val quadBuffer: FloatBuffer = ByteBuffer
            .allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_VERTICES)
                position(0)
            }

        private val fboSize = IntArray(2)
        private val tempHandle = IntArray(1)

        private var fullW = 0
        private var fullH = 0
        private var fboW = 0
        private var fboH = 0

        private var vboId = 0
        private var vaoId = 0
        private var progBlackHole = 0
        private var progUpscale = 0
        private var uResolutionLoc = -1
        private var uTimeLoc = -1
        private var uQualityLoc = -1
        private var uTextureLoc = -1

        private var fboId = 0
        private var fboTexId = 0
        private var glPronto = false

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            glPronto = false
            try {
                vboId = 0
                vaoId = 0
                fboId = 0
                fboTexId = 0

                progBlackHole = buildProgram(
                    HubBlackHoleShaders.VERTEX_SHADER,
                    HubBlackHoleShaders.BLACK_HOLE_FRAGMENT_SHADER
                )
                progUpscale = buildProgram(
                    HubBlackHoleShaders.VERTEX_SHADER,
                    HubBlackHoleShaders.UPSCALE_FRAGMENT_SHADER
                )
                if (progBlackHole == 0 || progUpscale == 0) {
                    KenkernLog.e(TAG, "shader do buraco negro não compilou — usando fallback sólido")
                    return
                }

                uResolutionLoc = GLES30.glGetUniformLocation(progBlackHole, "uResolution")
                uTimeLoc = GLES30.glGetUniformLocation(progBlackHole, "uTime")
                uQualityLoc = GLES30.glGetUniformLocation(progBlackHole, "uQuality")
                uTextureLoc = GLES30.glGetUniformLocation(progUpscale, "uTexture")

                GLES30.glGenVertexArrays(1, tempHandle, 0)
                vaoId = tempHandle[0]
                GLES30.glGenBuffers(1, tempHandle, 0)
                vboId = tempHandle[0]

                GLES30.glBindVertexArray(vaoId)
                GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
                quadBuffer.position(0)
                GLES30.glBufferData(
                    GLES30.GL_ARRAY_BUFFER,
                    QUAD_VERTICES.size * 4,
                    quadBuffer,
                    GLES30.GL_STATIC_DRAW
                )
                val stride = 4 * 4
                GLES30.glEnableVertexAttribArray(0)
                GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, stride, 0)
                GLES30.glEnableVertexAttribArray(1)
                GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 2 * 4)
                GLES30.glBindVertexArray(0)

                glPronto = true
            } catch (t: Throwable) {
                KenkernLog.e(TAG, "erro em onSurfaceCreated do buraco negro", t)
                glPronto = false
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            fullW = width
            fullH = height
            if (!glPronto) return
            try {
                HubCena2D.resolucaoFundo(width, height, fboSize)
                val novoFboW = fboSize[0]
                val novoFboH = fboSize[1]
                if (novoFboW != fboW || novoFboH != fboH || fboId == 0) {
                    alocarFbo(novoFboW, novoFboH)
                }
            } catch (t: Throwable) {
                KenkernLog.e(TAG, "erro em onSurfaceChanged do buraco negro", t)
                glPronto = false
            }
        }

        override fun onDrawFrame(gl: GL10?) {
            if (!glPronto || fboId == 0 || fullW <= 0 || fullH <= 0) {
                GLES30.glClearColor(0.05f, 0.05f, 0.08f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                return
            }
            try {
                // Passagem 1: buraco negro a 50% de resolução no FBO
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
                GLES30.glViewport(0, 0, fboW, fboH)
                GLES30.glUseProgram(progBlackHole)
                GLES30.glUniform2f(uResolutionLoc, fboW.toFloat(), fboH.toFloat())
                GLES30.glUniform1f(uTimeLoc, tempoSegundos)
                GLES30.glUniform1f(uQualityLoc, qualidade.uQuality)
                GLES30.glBindVertexArray(vaoId)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

                // Passagem 2: ampliar o FBO para a tela inteira com filtro GL_LINEAR
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(0, 0, fullW, fullH)
                GLES30.glUseProgram(progUpscale)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId)
                GLES30.glUniform1i(uTextureLoc, 0)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

                GLES30.glBindVertexArray(0)
            } catch (t: Throwable) {
                KenkernLog.e(TAG, "erro em onDrawFrame do buraco negro", t)
            }
        }

        private fun alocarFbo(w: Int, h: Int) {
            if (fboId != 0) {
                tempHandle[0] = fboId
                GLES30.glDeleteFramebuffers(1, tempHandle, 0)
                fboId = 0
            }
            if (fboTexId != 0) {
                tempHandle[0] = fboTexId
                GLES30.glDeleteTextures(1, tempHandle, 0)
                fboTexId = 0
            }

            fboW = w
            fboH = h

            GLES30.glGenTextures(1, tempHandle, 0)
            fboTexId = tempHandle[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTexId)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D,
                0,
                GLES30.GL_RGBA8,
                w,
                h,
                0,
                GLES30.GL_RGBA,
                GLES30.GL_UNSIGNED_BYTE,
                null
            )
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

            GLES30.glGenFramebuffers(1, tempHandle, 0)
            fboId = tempHandle[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
            GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER,
                GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D,
                fboTexId,
                0
            )
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                KenkernLog.e(TAG, "FBO incompleto no buraco negro do Hub: status=$status")
                glPronto = false
            }
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES30.glCreateShader(type)
            if (shader == 0) return 0
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, tempHandle, 0)
            if (tempHandle[0] == 0) {
                val info = GLES30.glGetShaderInfoLog(shader)
                KenkernLog.e(TAG, "falha ao compilar shader ($type): $info")
                GLES30.glDeleteShader(shader)
                return 0
            }
            return shader
        }

        private fun buildProgram(vsSource: String, fsSource: String): Int {
            val vs = compileShader(GLES30.GL_VERTEX_SHADER, vsSource)
            val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fsSource)
            if (vs == 0 || fs == 0) {
                if (vs != 0) GLES30.glDeleteShader(vs)
                if (fs != 0) GLES30.glDeleteShader(fs)
                return 0
            }
            val program = GLES30.glCreateProgram()
            if (program == 0) return 0
            GLES30.glAttachShader(program, vs)
            GLES30.glAttachShader(program, fs)
            GLES30.glLinkProgram(program)
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, tempHandle, 0)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            if (tempHandle[0] == 0) {
                val info = GLES30.glGetProgramInfoLog(program)
                KenkernLog.e(TAG, "falha ao linkar programa GL do Hub: $info")
                GLES30.glDeleteProgram(program)
                return 0
            }
            return program
        }
    }

    companion object {
        private const val TAG = "Kenkern/HubGL"
        private val QUAD_VERTICES = floatArrayOf(
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
        )
    }
}
