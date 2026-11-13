package com.kenkern.engine.render

import android.opengl.GLES30
import android.util.Log
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Mat4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Implementação OpenGL ES 3.0 do RenderBackend (minSdk 26).
 * Um único programa de shader com VAOs; os uniforms de luz vêm da LightingPipeline.
 *
 * ANTI-CRASH: [initialize] NUNCA lança exceção — se a compilação/linkagem do
 * shader principal falhar (GPU restrita, driver buggy), um shader de fallback
 * mínimo é compilado; se até isso falhar, o backend entra em modo degradado
 * (apenas clear) e a UI mostra o aviso. Todas as chamadas são tolerantes a
 * handles inválidos e erros de GL sem derrubar o processo.
 */
class GlesBackend : RenderBackend {

    override val name: String = "OpenGL ES 3.0"

    override var viewportWidth: Int = 1
    override var viewportHeight: Int = 1

    /** False se nem o shader de fallback compilou: backend só limpa a tela. */
    @Volatile
    var isDegraded: Boolean = false
        private set

    private var program = 0
    private var uMvp = 0
    private var uModel = 0
    private var uColor = 0
    private var uLightDir = 0
    private var uAmbient = 0
    private var uLightColor = 0
    private var uLightIntensity = 0

    private class GpuMesh(
        val vao: Int,
        val vbo: Int,
        val ibo: Int,
        val indexCount: Int,
        val indexType: Int
    )

    private class GpuLines(val vao: Int, val vbo: Int)

    private val meshes = HashMap<Int, GpuMesh>()
    private val lineBuffers = HashMap<Int, GpuLines>()
    private var nextHandle = 1

    private val mvpScratch = Mat4()
    private val uniformScratch = FloatArray(4)

    private val vertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        out vec3 vNormal;
        void main() {
            vNormal = mat3(uModel) * aNormal;
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val fragmentShader = """
        #version 300 es
        precision mediump float;
        in vec3 vNormal;
        uniform vec4 uColor;
        uniform vec3 uLightDir;
        uniform vec3 uAmbient;
        uniform vec3 uLightColor;
        uniform float uLightIntensity;
        out vec4 fragColor;
        void main() {
            vec3 n = normalize(vNormal);
            float diff = max(dot(n, normalize(-uLightDir)), 0.0);
            vec3 rgb = uColor.rgb * (uAmbient + uLightColor * diff * uLightIntensity);
            fragColor = vec4(rgb, uColor.a);
        }
    """.trimIndent()

    /** Fallback mínimo: cor sólida, sem iluminação — roda em praticamente qualquer GPU. */
    private val fallbackVertexShader = """
        #version 300 es
        layout(location = 0) in vec3 aPos;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        void main() {
            gl_Position = uMVP * vec4(aPos, 1.0);
        }
    """.trimIndent()

    private val fallbackFragmentShader = """
        #version 300 es
        precision mediump float;
        uniform vec4 uColor;
        out vec4 fragColor;
        void main() {
            fragColor = uColor;
        }
    """.trimIndent()

    override fun initialize(): Boolean {
        GLES30.glDisable(GLES30.GL_DITHER)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)
        GLES30.glFrontFace(GLES30.GL_CCW)

        // 1) Tenta o programa completo; 2) fallback mínimo; 3) modo degradado.
        program = buildProgram(vertexShader, fragmentShader)
        if (program == 0) {
            Log.w(TAG, "Shader principal falhou; tentando fallback mínimo")
            program = buildProgram(fallbackVertexShader, fallbackFragmentShader)
        }
        if (program == 0) {
            Log.e(TAG, "Nenhum shader compilou: backend em modo degradado (somente clear)")
            isDegraded = true
            return true // app continua vivo; viewport mostra só o fundo
        }

        uMvp = GLES30.glGetUniformLocation(program, "uMVP")
        uModel = GLES30.glGetUniformLocation(program, "uModel")
        uColor = GLES30.glGetUniformLocation(program, "uColor")
        uLightDir = GLES30.glGetUniformLocation(program, "uLightDir")
        uAmbient = GLES30.glGetUniformLocation(program, "uAmbient")
        uLightColor = GLES30.glGetUniformLocation(program, "uLightColor")
        uLightIntensity = GLES30.glGetUniformLocation(program, "uLightIntensity")

        GLES30.glClearColor(0.075f, 0.075f, 0.105f, 1f)
        drainGlErrors()
        return true
    }

    /** Compila + linka sem lançar; retorna 0 em qualquer falha. */
    private fun buildProgram(vsSrc: String, fsSrc: String): Int {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vsSrc) ?: return 0
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fsSrc) ?: run {
            GLES30.glDeleteShader(vs)
            return 0
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, vs)
        GLES30.glAttachShader(p, fs)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val status = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Link falhou: ${GLES30.glGetProgramInfoLog(p)}")
            GLES30.glDeleteProgram(p)
            drainGlErrors()
            return 0
        }
        return p
    }

    /** Compila um shader sem lançar; retorna null em falha. */
    private fun compile(type: Int, source: String): Int? {
        val shader = GLES30.glCreateShader(type)
        if (shader == 0) return null
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.w(TAG, "Compile falhou: ${GLES30.glGetShaderInfoLog(shader)}")
            GLES30.glDeleteShader(shader)
            drainGlErrors()
            return null
        }
        return shader
    }

    private fun drainGlErrors() {
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) { /* descarta erros pendentes */ }
    }

    override fun resize(width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun beginFrame(clearR: Float, clearG: Float, clearB: Float, clearA: Float) {
        GLES30.glClearColor(clearR, clearG, clearB, clearA)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    override fun setLighting(
        dirX: Float, dirY: Float, dirZ: Float,
        ambientR: Float, ambientG: Float, ambientB: Float,
        lightR: Float, lightG: Float, lightB: Float,
        intensity: Float
    ) {
        if (program == 0) return
        GLES30.glUseProgram(program)
        uniformScratch[0] = dirX; uniformScratch[1] = dirY; uniformScratch[2] = dirZ
        GLES30.glUniform3fv(uLightDir, 1, uniformScratch, 0)
        uniformScratch[0] = ambientR; uniformScratch[1] = ambientG; uniformScratch[2] = ambientB
        GLES30.glUniform3fv(uAmbient, 1, uniformScratch, 0)
        uniformScratch[0] = lightR; uniformScratch[1] = lightG; uniformScratch[2] = lightB
        GLES30.glUniform3fv(uLightColor, 1, uniformScratch, 0)
        GLES30.glUniform1f(uLightIntensity, intensity)
        GLES30.glUseProgram(0)
    }

    override fun createMesh(mesh: MeshData): Int {
        if (mesh.vertexCount <= 0 || mesh.indices.isEmpty()) return 0
        val vertexCount = mesh.vertexCount

        val interleaved = runCatching {
            ByteBuffer.allocateDirect(vertexCount * STRIDE).order(ByteOrder.nativeOrder())
        }.getOrNull() ?: return 0
        val fb = interleaved.asFloatBuffer()
        for (v in 0 until vertexCount) {
            fb.put(mesh.positions[v * 3]); fb.put(mesh.positions[v * 3 + 1]); fb.put(mesh.positions[v * 3 + 2])
            fb.put(mesh.normals[v * 3]); fb.put(mesh.normals[v * 3 + 1]); fb.put(mesh.normals[v * 3 + 2])
        }
        fb.position(0)

        val useShort = vertexCount <= 65535
        val bytesPerIndex = if (useShort) 2 else 4
        val indexBuffer = runCatching {
            ByteBuffer.allocateDirect(mesh.indices.size * bytesPerIndex).order(ByteOrder.nativeOrder())
        }.getOrNull() ?: return 0
        if (useShort) {
            val sb = indexBuffer.asShortBuffer()
            for (i in mesh.indices) sb.put(i.toShort())
            sb.position(0)
        } else {
            val ib = indexBuffer.asIntBuffer()
            for (i in mesh.indices) ib.put(i)
            ib.position(0)
        }

        val vao = IntArray(1)
        val vbo = IntArray(1)
        val ebo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glGenBuffers(1, ebo, 0)
        if (vao[0] == 0 || vbo[0] == 0 || ebo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            if (ebo[0] != 0) GLES30.glDeleteBuffers(1, ebo, 0)
            drainGlErrors()
            return 0
        }

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertexCount * STRIDE, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE, 12)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ebo[0])
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.indices.size * bytesPerIndex, indexBuffer, GLES30.GL_STATIC_DRAW
        )
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        meshes[handle] = GpuMesh(
            vao[0], vbo[0], ebo[0], mesh.indices.size,
            if (useShort) GLES30.GL_UNSIGNED_SHORT else GLES30.GL_UNSIGNED_INT
        )
        return handle
    }

    override fun createLineBuffer(positions: FloatArray): Int {
        if (positions.isEmpty()) return 0
        val vao = IntArray(1)
        val vbo = IntArray(1)
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        if (vao[0] == 0 || vbo[0] == 0) {
            if (vao[0] != 0) GLES30.glDeleteVertexArrays(1, vao, 0)
            if (vbo[0] != 0) GLES30.glDeleteBuffers(1, vbo, 0)
            drainGlErrors()
            return 0
        }
        val fb: FloatBuffer = ByteBuffer.allocateDirect(positions.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        fb.put(positions)
        fb.position(0)

        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, positions.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glVertexAttrib3f(1, 0f, 1f, 0f)
        GLES30.glBindVertexArray(0)
        drainGlErrors()

        val handle = nextHandle++
        lineBuffers[handle] = GpuLines(vao[0], vbo[0])
        return handle
    }

    override fun drawMesh(handle: Int, model: Mat4, viewProj: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (program == 0) return
        val gpu = meshes[handle] ?: return
        mvpScratch.multiply(viewProj, model)
        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpScratch.m, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uColor, 1, uniformScratch, 0)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, gpu.indexCount, gpu.indexType, 0)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    override fun drawLines(handle: Int, vertexCount: Int, mvp: Mat4, r: Float, g: Float, b: Float, a: Float) {
        if (program == 0 || vertexCount <= 0) return
        val gpu = lineBuffers[handle] ?: return
        GLES30.glUseProgram(program)
        GLES30.glBindVertexArray(gpu.vao)
        GLES30.glUniformMatrix4fv(uMvp, 1, false, mvp.m, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, mvp.m, 0)
        uniformScratch[0] = r; uniformScratch[1] = g; uniformScratch[2] = b; uniformScratch[3] = a
        GLES30.glUniform4fv(uColor, 1, uniformScratch, 0)
        GLES30.glDrawArrays(GLES30.GL_LINES, 0, vertexCount)
        GLES30.glBindVertexArray(0)
        GLES30.glUseProgram(0)
    }

    override fun destroyMesh(handle: Int) {
        val gpu = meshes.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(2); bufs[0] = gpu.vbo; bufs[1] = gpu.ibo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(2, bufs, 0)
    }

    override fun destroyLineBuffer(handle: Int) {
        val gpu = lineBuffers.remove(handle) ?: return
        val vao = IntArray(1); vao[0] = gpu.vao
        val bufs = IntArray(1); bufs[0] = gpu.vbo
        GLES30.glDeleteVertexArrays(1, vao, 0)
        GLES30.glDeleteBuffers(1, bufs, 0)
    }

    override fun endFrame() {
        // Swap é feito pelo GLSurfaceView.
    }

    override fun destroy() {
        for (mesh in meshes.values) {
            val vao = IntArray(1); vao[0] = mesh.vao
            val bufs = IntArray(2); bufs[0] = mesh.vbo; bufs[1] = mesh.ibo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(2, bufs, 0)
        }
        meshes.clear()
        for (line in lineBuffers.values) {
            val vao = IntArray(1); vao[0] = line.vao
            val bufs = IntArray(1); bufs[0] = line.vbo
            GLES30.glDeleteVertexArrays(1, vao, 0)
            GLES30.glDeleteBuffers(1, bufs, 0)
        }
        lineBuffers.clear()
        if (program != 0) {
            GLES30.glDeleteProgram(program)
            program = 0
        }
        drainGlErrors()
    }

    companion object {
        private const val TAG = "Kenkern/GlesBackend"
        private const val STRIDE = 24 // 3 pos + 3 normal (floats)
    }
}
