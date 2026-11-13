package com.kenkern.engine.render

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.engine.FixedStepLoop
import com.kenkern.engine.engine.PlayState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Controles do motor expostos ao editor (Play/Pause/Stop). */
interface EngineControls {
    fun setPlayState(state: PlayState)
    fun currentPlayState(): PlayState
}

/**
 * GLSurfaceView da KENKERN: embutido no editor via AndroidView.
 * Loop próprio com timestep fixo; câmera orbital por toque.
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

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 0, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(glRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        onEngineReady(controls)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        return touch.onTouch(event)
    }

    private inner class KenkernGLRenderer : Renderer {
        private val backend = GlesBackend()
        private val engineRenderer = EngineRenderer()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            backend.initialize()
            engineRenderer.initialize(backend)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            backend.resize(width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val scene = sceneProvider()
            loop.tick(System.nanoTime()) { dt ->
                scene?.updateFunctions(dt, loop.engineTime)
            }
            // Matrizes de mundo sempre atualizadas (mesmo pausado, o Inspetor edita transform).
            scene?.updateWorldTransforms()
            engineRenderer.renderFrame(backend, scene, camera, loop.engineTime, selectionProvider())
        }
    }
}
