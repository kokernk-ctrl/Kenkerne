package com.kenkern.engine.render

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.RotationFromMatrix
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.engine.FixedStepLoop
import com.kenkern.engine.engine.PlayState
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs

/** Controles do motor expostos ao editor (Play/Pause/Stop). */
interface EngineControls {
    fun setPlayState(state: PlayState)
    fun currentPlayState(): PlayState
}

/**
 * GLSurfaceView da KENKERN: embutido no editor via AndroidView.
 * Loop próprio com timestep fixo; câmera orbital por toque.
 *
 * SELEÇÃO E GIZMOS (Fase 1):
 *  - tap curto (< 200 ms, < 10 px) => picking (raio da câmera × AABB de cada Nick,
 *    no thread GL, com as matrizes de mundo recém-atualizadas); resultado via
 *    [onNickPicked]. Arrasto SEM alça move só a câmera (nunca muda seleção/transform).
 *  - duplo toque => foco: no vazio enquadra a CENA toda; sobre seleção, o Nick.
 *  - botão FOCAR (F) => enquadra o Nick selecionado ([focusSelected]).
 *  - toque sobre alça do gizmo => arrasto no thread de UI (posse decidida no DOWN,
 *    requestDisallowInterceptTouchEvent); ao soltar, [onGizmoDragEnd] entrega o
 *    snapshot inicial para UM único Command de undo.
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
    private val onEngineReady: (EngineControls) -> Unit,
    private val onNickPicked: (ReferenceId?) -> Unit = {},
    private val onGizmoDragEnd: (ReferenceId, TransformSnapshot, TransformSnapshot) -> Unit = { _, _, _ -> },
    private val onGizmoLiveUpdate: () -> Unit = {},
    private val onFirstTouch: () -> Unit = {}
) : GLSurfaceView(context) {

    val camera = OrbitCamera()
    val loop = FixedStepLoop()
    val gizmoState = GizmoSharedState()

    /** True = construção do gizmo falhou e caiu no modo VAZIO (aviso na StatusBar). */
    @Volatile
    var isGizmoDegraded: Boolean = false
        private set

    /**
     * Construção do gizmo blindada (runCatching): qualquer exceção na
     * inicialização do TransformGizmo (ordem de campos, OOM, etc.) cai para um
     * GIZMO VAZIO — o viewport renderiza sem gizmo, o hit-test devolve NONE e
     * a StatusBar mostra o aviso. NUNCA derruba o editor.
     */
    private val gizmo: TransformGizmo = runCatching { TransformGizmo() }
        .getOrElse {
            Log.e(TAG, "Construção do TransformGizmo falhou: ${it.message} — usando gizmo vazio", it)
            isGizmoDegraded = true
            TransformGizmo.empty()
        }
    private val mainHandler = Handler(Looper.getMainLooper())

    private val density = resources.displayMetrics.density
    private val touch = TouchController(
        camera,
        tapSlopPx = 10f * density,
        onTap = { x, y -> requestPick(x, y) },
        onDoubleTap = { _, _ -> handleDoubleTap() },
        gizmoHandler = GizmoTouchHandler()
    )
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

    /** Configuração do gizmo vinda da TopBar (Mover/Girar/Escalar, Local/Global, Snap). */
    fun setGizmoConfig(mode: GizmoMode, space: GizmoSpace, snapEnabled: Boolean) {
        gizmoState.mode = mode
        gizmoState.space = space
        gizmoState.snapEnabled = snapEnabled
    }

    // ---------- Picking (pedido da UI, resolvido no thread GL) ----------

    @Volatile
    private var pickPending = false
    @Volatile
    private var pickNdcX = 0f
    @Volatile
    private var pickNdcY = 0f

    private fun requestPick(xPx: Float, yPx: Float) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        pickNdcX = (xPx / w) * 2f - 1f
        pickNdcY = 1f - (yPx / h) * 2f
        pickPending = true
    }

    // ---------- FOCO (F / toque duplo): pedido da UI, resolvido no thread GL ----------

    @Volatile
    private var focusPending = false
    @Volatile
    private var focusAllNicks = false

    private val focusStack = arrayOfNulls<Nick>(512)
    private val focusMin = Vec3()
    private val focusMax = Vec3()
    private val focusCenter = Vec3()

    /** Botão FOCAR: enquadra o Nick selecionado; sem seleção, a cena toda. */
    fun focusSelected() {
        focusAllNicks = selectionProvider() == null
        focusPending = true
    }

    /** Duplo toque: vazio => cena toda; sobre um Nick selecionado => o Nick. */
    private fun handleDoubleTap() {
        focusAllNicks = selectionProvider() == null
        focusPending = true
    }

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
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onFirstTouch()
        }
        val handled = runCatching { touch.onTouch(event) }.getOrDefault(false)
        // Posse do gesto decidida no DOWN: se o gizmo capturou a alça, nenhum pai
        // intercepta o gesto até o ACTION_UP (o sistema limpa a flag no próximo DOWN).
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            runCatching { touch.isGizmoCapturedNow() }.getOrDefault(false)
        ) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return handled
    }

    // ---------- Handler de arrasto do gizmo (thread de UI, zero alocação) ----------

    private inner class GizmoTouchHandler : GizmoDragHandler {
        private val dragProj = Mat4()
        private val dragView = Mat4()
        private val dragVp = Mat4()
        private val dragWorldPos = Vec3()
        private val dragWorldRot = Quaternion()
        private val dragExtractor = RotationFromMatrix()
        private var dragNick: Nick? = null

        private fun refreshMatrices(w: Float, h: Float) {
            val aspect = w / h
            camera.projection(dragProj, aspect)
            camera.view(dragView)
            dragVp.multiply(dragProj, dragView)
        }

        private fun extractRotation(m: Mat4, out: Quaternion) {
            val mm = m.m
            val sx = kotlin.math.sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
            val sy = kotlin.math.sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
            val sz = kotlin.math.sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
            if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
                dragExtractor.set(
                    mm[0] / sx, mm[4] / sy, mm[8] / sz,
                    mm[1] / sx, mm[5] / sy, mm[9] / sz,
                    mm[2] / sx, mm[6] / sy, mm[10] / sz
                )
                dragExtractor.toMatrixComponents(out)
            } else {
                out.setIdentity()
            }
        }

        override fun onGizmoDown(x: Float, y: Float): Boolean {
            val scene = sceneProvider() ?: return false
            val sel = selectionProvider() ?: return false
            val nick = scene.findNick(sel) ?: return false
            if (!nick.active || !nick.visible) return false
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 1f || h < 1f) return false
            refreshMatrices(w, h)
            dragWorldPos.set(nick.transform.worldMatrix.m[12], nick.transform.worldMatrix.m[13], nick.transform.worldMatrix.m[14])
            extractRotation(nick.transform.worldMatrix, dragWorldRot)
            val ss = gizmo.screenScaleFor(camera, dragWorldPos)
            val radius = 28f * density // tolerância de hit-test: 28dp
            val handle = gizmo.hitTest(
                x, y, dragVp, w, h, dragWorldPos, dragWorldRot, ss,
                gizmoState.space, gizmoState.mode, radius
            )
            if (handle == GizmoHandle.NONE) return false
            dragNick = nick
            gizmo.beginDrag(nick, handle, x, y, camera, dragVp, w, h, w / h, gizmoState.space, ss)
            gizmoState.highlighted = handle
            return true
        }

        override fun onGizmoMove(x: Float, y: Float) {
            val nick = dragNick ?: return
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 1f || h < 1f) return
            refreshMatrices(w, h)
            gizmo.updateDrag(x, y, camera, dragVp, w, h, w / h, gizmoState.space, gizmoState.snapEnabled)
            onGizmoLiveUpdate() // Inspetor atualiza ao vivo durante o arrasto
        }

        override fun onGizmoUp(x: Float, y: Float) {
            val nick = dragNick
            gizmoState.highlighted = GizmoHandle.NONE
            dragNick = null
            if (nick != null) {
                val start = gizmo.endDrag()
                if (start != null) {
                    val end = nick.transform.snapshot()
                    if (start != end) {
                        onGizmoDragEnd(nick.id, start, end) // UM Command por arrasto
                    }
                }
            }
        }
    }

    /**
     * Picking no thread GL: raio da câmera contra a AABB (em mundo) de cada Nick
     * ativo/visível; escolhe o mais próximo. Slab test sem alocação.
     */
    private fun pickNickAt(scene: Scene, aspect: Float): ReferenceId? {
        val origin = Vec3()
        val dir = Vec3()
        camera.screenRay(pickNdcX, pickNdcY, aspect, origin, dir)
        val stack = arrayOfNulls<Nick>(256)
        var top = 0
        for (r in scene.roots.indices) {
            if (top >= stack.size) break
            stack[top++] = scene.roots[r]
        }
        var bestId: ReferenceId? = null
        var bestT = Float.MAX_VALUE
        while (top > 0) {
            val nick = stack[--top] ?: continue
            if (nick.active && nick.visible) {
                val m = nick.transform.worldMatrix.m
                val cx = m[12]; val cy = m[13]; val cz = m[14]
                // meia-diagonal da AABB em mundo (box conservativa p/ rotação)
                val ex = 0.5f * (abs(m[0]) + abs(m[4]) + abs(m[8]))
                val ey = 0.5f * (abs(m[1]) + abs(m[5]) + abs(m[9]))
                val ez = 0.5f * (abs(m[2]) + abs(m[6]) + abs(m[10]))
                val t = rayAabb(origin, dir, cx, cy, cz, ex, ey, ez)
                if (t >= 0f && t < bestT) {
                    bestT = t
                    bestId = nick.id
                }
            }
            val children = nick.children
            for (ci in children.indices) {
                if (top >= stack.size) break
                stack[top++] = children[ci]
            }
        }
        return bestId
    }

    /** Slab test raio-AABB; retorna t (>= 0) ou -1 se não há interseção. */
    private fun rayAabb(
        o: Vec3, d: Vec3,
        cx: Float, cy: Float, cz: Float,
        ex: Float, ey: Float, ez: Float
    ): Float {
        var tmin = 0f
        var tmax = Float.MAX_VALUE
        // X
        if (abs(d.x) < 1e-9f) {
            if (abs(o.x - cx) > ex) return -1f
        } else {
            var t1 = (cx - ex - o.x) / d.x
            var t2 = (cx + ex - o.x) / d.x
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        // Y
        if (abs(d.y) < 1e-9f) {
            if (abs(o.y - cy) > ey) return -1f
        } else {
            var t1 = (cy - ey - o.y) / d.y
            var t2 = (cy + ey - o.y) / d.y
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        // Z
        if (abs(d.z) < 1e-9f) {
            if (abs(o.z - cz) > ez) return -1f
        } else {
            var t1 = (cz - ez - o.z) / d.z
            var t2 = (cz + ez - o.z) / d.z
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        return tmin
    }

    /**
     * FOCO no thread GL: calcula a AABB (do Nick selecionado ou da cena toda)
     * e pede à câmera que enquadre com a distância calculada pelo raio da AABB.
     * Sem alocação (pilha e vetores pré-alocados).
     */
    private fun applyFocus(scene: Scene) {
        val selected = selectionProvider()
        var hasBox = false
        var top = 0
        focusStack.fill(null)
        for (r in scene.roots.indices) {
            if (top >= focusStack.size) break
            focusStack[top++] = scene.roots[r]
        }
        while (top > 0) {
            val nick = focusStack[--top] ?: continue
            if (nick.active && nick.visible && (focusAllNicks || nick.id == selected)) {
                val m = nick.transform.worldMatrix.m
                val cx = m[12]; val cy = m[13]; val cz = m[14]
                // meia-extensão conservativa da AABB em mundo (box p/ rotação)
                val ex = 0.5f * (abs(m[0]) + abs(m[4]) + abs(m[8]))
                val ey = 0.5f * (abs(m[1]) + abs(m[5]) + abs(m[9]))
                val ez = 0.5f * (abs(m[2]) + abs(m[6]) + abs(m[10]))
                if (!hasBox) {
                    focusMin.set(cx - ex, cy - ey, cz - ez)
                    focusMax.set(cx + ex, cy + ey, cz + ez)
                    hasBox = true
                } else {
                    focusMin.set(
                        minOf(focusMin.x, cx - ex), minOf(focusMin.y, cy - ey), minOf(focusMin.z, cz - ez)
                    )
                    focusMax.set(
                        maxOf(focusMax.x, cx + ex), maxOf(focusMax.y, cy + ey), maxOf(focusMax.z, cz + ez)
                    )
                }
            }
            val children = nick.children
            for (ci in children.indices) {
                if (top >= focusStack.size) break
                focusStack[top++] = children[ci]
            }
        }
        if (!hasBox) {
            // cena vazia: enquadra o ponto padrão da cena
            camera.focusOn(0f, 0.8f, 0f, 4f)
            return
        }
        focusCenter.set(
            (focusMin.x + focusMax.x) * 0.5f,
            (focusMin.y + focusMax.y) * 0.5f,
            (focusMin.z + focusMax.z) * 0.5f
        )
        val dx = (focusMax.x - focusMin.x) * 0.5f
        val dy = (focusMax.y - focusMin.y) * 0.5f
        val dz = (focusMax.z - focusMin.z) * 0.5f
        val radius = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.5f)
        // Guarda ANTI-NaN: AABB patológica (transform inválido) -> foco no padrão.
        if (!(focusCenter.x.isFinite() && focusCenter.y.isFinite() && focusCenter.z.isFinite())) {
            camera.focusOn(0f, 0.8f, 0f, 4f)
            return
        }
        camera.focusOn(focusCenter.x, focusCenter.y, focusCenter.z, radius)
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

        // Marcador de progresso: "primeiro frame" só é gravado 1x por sessão.
        private var firstFrameMarked = false

        fun currentFps(): Int = engineRenderer.fps

        fun currentUpdateMs(): Float = engineRenderer.updateMs

        fun currentDrawMs(): Float = engineRenderer.drawMs

        fun currentDrawCalls(): Int = engineRenderer.drawCount

        fun isKernRoundtripOk(): Boolean = engineRenderer.isKernRoundtripOk()

        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: EGLConfig?) {
            // Marcador de progresso: EGL criou a surface (o trecho entre "EditorScreen
            // composto" e esta linha cobre eglCreateContext/eglCreateWindowSurface,
            // janela típica de crash NATIVO em driver GL).
            BootTrace.mark("surface criada")
            surfaceReady = false
            isEngineHealthy = false
            runCatching {
                backend.destroy() // libera restos de contexto anterior
            }
            runCatching {
                engineRenderer.initialize(backend, gizmo)
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
                    // Picking pendente: roda com as matrizes de mundo recém-atualizadas.
                    if (pickPending) {
                        pickPending = false
                        val s = scene
                        if (s != null && s.roots.isNotEmpty()) {
                            val aspect = backend.viewportWidth.toFloat() /
                                backend.viewportHeight.coerceAtLeast(1).toFloat()
                            val picked = runCatching { pickNickAt(s, aspect) }.getOrNull()
                            mainHandler.post { onNickPicked(picked) }
                        }
                    }
                    // FOCO pendente (botão F / duplo toque): AABB e ajuste da câmera.
                    if (focusPending) {
                        focusPending = false
                        val s = scene
                        if (s != null) runCatching { applyFocus(s) }
                    }
                    engineRenderer.renderFrame(
                        backend, scene, camera, loop.engineTime, selectionProvider(), gizmoState
                    )
                    // Marcador de progresso: primeiro frame COMPLETO no thread GL.
                    // Gravado DEPOIS do renderFrame: crash nativo dentro do draw
                    // deixa a última etapa em "shader compilado".
                    if (!firstFrameMarked) {
                        firstFrameMarked = true
                        BootTrace.mark("primeiro frame")
                    }
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
