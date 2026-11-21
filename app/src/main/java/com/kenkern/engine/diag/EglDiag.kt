package com.kenkern.engine.diag

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface

/**
 * Contexto EGL OFFSCREEN (pbuffer 64×64) para as checagens GL do Diagnóstico.
 *
 * CAUSA RAIZ D4 (v0.1.16): era 1×1 e a checagem "etc2" desenhava num viewport
 * 4×4 e lia o pixel (1,2) — LEITURA FORA DA SUPERFÍCIE = GL_INVALID_OPERATION
 * e buffer zerado: no aparelho Mali-G57 o pixel saía (0,0,0) mesmo com o
 * upload e o draw corretos. O pbuffer agora tem 64×64 (custo de memória
 * desprezível, ainda offscreen e independente do viewport do editor) e a
 * checagem etc2 usa framebuffer offscreen PRÓPRIO com status conferido.
 *
 * Cria display/config/superfície/contexto GLES 3.0 próprios, INDEPENDENTES do
 * viewport do editor (que continua intocado), executa as checagens NA MESMA
 * THREAD em que foi criado (contexto EGL é thread-local) e libera tudo no fim.
 *
 * Nunca lança: qualquer falha de EGL é reportada via [criar] como texto e a
 * checagem correspondente marca FALHOU com o motivo.
 */
class EglDiag private constructor(
    private val display: EGLDisplay,
    private val surface: EGLSurface,
    private val context: EGLContext
) {
    private var liberado = false

    /** Libera contexto/superfície/display NA MESMA THREAD do [criar]. Idempotente. */
    fun liberar() {
        if (liberado) return
        liberado = true
        runCatching {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }.onFailure { KenkernLog.w("Kenkern/EglDiag", "liberar() do contexto pbuffer falhou — recurso pode vazar", it) }
    }

    companion object {
        /**
         * Cria o contexto pbuffer GLES 3.0 na thread atual.
         * Retorna null em falha (motivo em [onErro]); nunca lança.
         */
        fun criar(onErro: (String) -> Unit): EglDiag? = runCatching {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) {
                onErro("eglGetDisplay falhou"); return@runCatching null
            }
            val versaoMaior = intArrayOf(0)
            val versaoMenor = intArrayOf(0)
            if (!EGL14.eglInitialize(display, versaoMaior, 0, versaoMenor, 0)) {
                onErro("eglInitialize falhou"); return@runCatching null
            }

            // Tentativa 1: RGBA8 + depth 24 (mesma cadeia do viewport).
            // Tentativa 2: qualquer config com pbuffer + ES3 (drivers econômicos).
            val config = escolherConfig(display) ?: run {
                onErro("nenhuma EGLConfig com pbuffer + GLES 3"); return@runCatching null
            }

            // 64×64 (D4): as checagens desenham em viewport ≤ 16×16 e leem
            // pixels — 1×1 forçava leitura fora da superfície (pixel (0,0,0)).
            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 64,
                EGL14.EGL_HEIGHT, 64,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) {
                EGL14.eglTerminate(display)
                onErro("eglCreatePbufferSurface falhou"); return@runCatching null
            }

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglTerminate(display)
                onErro("eglCreateContext (GLES 3.0) falhou"); return@runCatching null
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
                EGL14.eglTerminate(display)
                onErro("eglMakeCurrent falhou"); return@runCatching null
            }
            EglDiag(display, surface, context)
        }.onFailure {
            onErro("exceção EGL: ${it.message ?: it.javaClass.simpleName}")
        }.getOrNull()

        private fun escolherConfig(display: EGLDisplay): EGLConfig? {
            val saida = arrayOfNulls<EGLConfig>(1)
            val contagem = IntArray(1)

            val rgbaD24 = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 0x0040, // EGL_OPENGL_ES3_BIT
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 24,
                EGL14.EGL_NONE
            )
            if (EGL14.eglChooseConfig(display, rgbaD24, 0, saida, 0, 1, contagem, 0) && contagem[0] > 0) {
                return saida[0]
            }

            val minima = intArrayOf(
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RENDERABLE_TYPE, 0x0040,
                EGL14.EGL_NONE
            )
            if (EGL14.eglChooseConfig(display, minima, 0, saida, 0, 1, contagem, 0) && contagem[0] > 0) {
                return saida[0]
            }
            return null
        }
    }
}
