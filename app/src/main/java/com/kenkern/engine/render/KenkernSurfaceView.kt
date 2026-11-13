package com.kenkern.engine.render

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.MotionEvent
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.engine.FixedStepLoop
import com.kenkern.engine.engine.PlayState
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/** Controles do motor expostos ao editor (Play/Pause/Stop). */
interface EngineControls {
    fun setPlayState(state: PlayState)
    fun currentPlayState(): PlayState
}

/**
 * GLSurfaceView da KENKERN: embutido no editor via AndroidView.
 * Loop próprio com timestep fixo; câmera orbital por toque.
 *
 * ANTI-CRASH (GPU restrita):
 *  - EGLConfigChooser com cadeia de fallback (RGBA8+D24 → RGBA8+D16 → RGB565 → qualquer config);
 *  - checagem prévia do suporte a GLES 3.0 (getDeviceInfo.reqGlesVersion);
 *  - TODOS os callbacks do renderer protegidos por try-catch — nenhum erro de
 *    driver derruba o processo; o viewport degrada para modo seguro (só clear).
 */
class KenkernSurfaceView(
    context: Context,
    private val sceneProvider: () -> Scene?,
    private val selectionProvider: () -> ReferenceId?,
    private val onEngineReady: (EngineControls) -> Unit
) : GLSurfaceView(context) {

    val camera = OrbitCamera()
    val loop = FixedStepLoop()
    private val touch = TouchController(camera)
    private val glRenderer = KenkernGLRenderer()
    private val controls = object : EngineControls {
        override fun setPlayState(state: PlayState) {
            queueEvent { loop.requestPlayState(state) }
        }

        override fun currentPlayState(): PlayState = loop.playState
    }

    /** True se o contexto GLES 3.0 foi aceito; false = modo de compatibilidade. */
    @Volatile
    var isEngineHealthy: Boolean = false
        private set

    /** FPS medido pelo renderer (lido pela barra de status; [tick] força recomposição). */
    fun rendererFps(tick: Int): Int = glRenderer.currentFps()

    /** ms de update (EMA); [tick] só força recomposição a 2 Hz, não entra no thread GL. */
    fun rendererUpdateMs(tick: Int): Float = glRenderer.currentUpdateMs()

    /** ms de draw (EMA); [tick] só força recomposição a 2 Hz. */
    fun rendererDrawMs(tick: Int): Float = glRenderer.currentDrawMs()

    /** Draw calls do último frame (grade + malhas); [tick] só força recomposição a 2 Hz. */
    fun rendererDrawCalls(tick: Int): Int = glRenderer.currentDrawCalls()

    /** True se o roundtrip .KERN (gravar/ler malha) passou nesta sessão de render. */
    fun isKernOk(): Boolean = glRenderer.isKernRoundtripOk()

    init {
        val es3 = deviceSupportsGles3(context)
        // Fábrica e chooser próprios definem a versão do contexto (ES3 com fallback ES2).
        setEGLContextFactory(SafeContextFactory())
        setEGLConfigChooser(SafeEGLConfigChooser(es3))
        preserveEGLContextOnPause = true
        setRenderer(glRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        onEngineReady(controls)
    }

    private fun deviceSupportsGles3(ctx: Context): Boolean = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = am?.deviceConfigurationInfo
        info == null || info.reqGlEsVersion >= 0x30000 // null = assume que suporta
    }.getOrDefault(true)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        return runCatching { touch.onTouch(event) }.getOrDefault(false)
    }

    /**
     * Fábrica de contexto tolerante: tenta ES 3.0 e cai para ES 2.0 se o driver
     * recusar (app continua renderizando com o shader de fallback do backend).
     */
    private class SafeContextFactory : GLSurfaceView.EGLContextFactory {
        override fun createContext(egl: EGL10, display: EGLDisplay, config: EGLConfig): javax.microedition.khronos.egl.EGLContext {
            val attrs = intArrayOf(0x3098 /*EGL_CONTEXT_CLIENT_VERSION*/, 3, EGL10.EGL_NONE)
            var ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrs)
            if (ctx == null || ctx === EGL10.EGL_NO_CONTEXT) {
                Log.w(TAG, "Contexto ES3 recusado; tentando ES2")
                val attrs2 = intArrayOf(0x3098, 2, EGL10.EGL_NONE)
                ctx = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrs2)
            }
            if (ctx == null || ctx === EGL10.EGL_NO_CONTEXT) {
                Log.e(TAG, "eglCreateContext falhou: 0x${Integer.toHexString(egl.eglGetError())}")
            }
            return ctx
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: javax.microedition.khronos.egl.EGLContext) {
            runCatching {
                if (context !== EGL10.EGL_NO_CONTEXT) {
                    egl.eglDestroyContext(display, context)
                }
            }
        }
    }

    /**
     * Chooser com fallback: tenta do melhor para o mais relaxado.
     * Nunca fica sem config a menos que o dispositivo não tenha EGL algum.
     */
    private class SafeEGLConfigChooser(private val wantEs3: Boolean) : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val renderable = if (wantEs3) 0x0040 /*EGL_OPENGL_ES3_BIT*/ else 4 /*ES2*/
            val candidates = listOf(
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 0, EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 0,
                    0x3040 /*EGL_RENDERABLE_TYPE*/, renderable, EGL10.EGL_NONE
                ),
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 0, EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE
                ),
                intArrayOf(
                    EGL10.EGL_RED_SIZE, 5, EGL10.EGL_GREEN_SIZE, 6, EGL10.EGL_BLUE_SIZE, 5,
                    EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_NONE
                ),
                intArrayOf(EGL10.EGL_DEPTH_SIZE, 8, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_NONE)
            )
            for (attr in candidates) {
                val num = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                val ok = runCatching {
                    egl.eglChooseConfig(display, attr, configs, 1, num) && num[0] > 0 && configs[0] != null
                }.getOrDefault(false)
                if (ok) return configs[0]!!
            }
            // Sem EGL no dispositivo: lança mensagem clara (GLSurfaceView reporta;
            // o app continua vivo porque o processo não tem GL nesta máquina).
            throw IllegalArgumentException("Nenhum config EGL disponível neste dispositivo")
        }
    }

    private inner class KenkernGLRenderer : Renderer {
        private val backend = GlesBackend()
        private val engineRenderer = EngineRenderer()
        private var surfaceReady = false

        fun currentFps(): Int = engineRenderer.fps

        fun currentUpdateMs(): Float = engineRenderer.updateMs

        fun currentDrawMs(): Float = engineRenderer.drawMs

        fun currentDrawCalls(): Int = engineRenderer.drawCount

        fun isKernRoundtripOk(): Boolean = engineRenderer.isKernRoundtripOk()

        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: EGLConfig?) {
            surfaceReady = false
            isEngineHealthy = false
            runCatching {
                backend.destroy() // libera restos de contexto anterior
            }
            runCatching {
                engineRenderer.initialize(backend)
                surfaceReady = true
                isEngineHealthy = true
                Log.i(TAG, "Viewport GL inicializado (${backend.name})")
            }.onFailure {
                Log.e(TAG, "Falha na inicialização GL (modo seguro)", it)
            }
        }

        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            runCatching { backend.resize(width, height) }
        }

        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            try {
                val scene = sceneProvider()
                // Instrumentação: ms de update (timestep fixo + world transforms).
                val updateStart = System.nanoTime()
                loop.tick(System.nanoTime()) { dt ->
                    scene?.updateFunctions(dt, loop.engineTime)
                }
                // Matrizes de mundo sempre atualizadas (mesmo pausado, o Inspetor edita transform).
                scene?.updateWorldTransforms()
                engineRenderer.reportUpdateMs((System.nanoTime() - updateStart) * 1e-6f)
                if (surfaceReady) {
                    engineRenderer.renderFrame(backend, scene, camera, loop.engineTime, selectionProvider())
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Frame descartado", e)
                runCatching {
                    backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
                    backend.endFrame()
                }
            }
        }
    }

    companion object {
        private const val TAG = "Kenkern/SurfaceView"
    }
}
