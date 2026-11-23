package com.kenkern.engine.render

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.RotationFromMatrix
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.TransformSnapshot
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.engine.FixedStepLoop
import com.kenkern.engine.engine.NickLuaScripts
import com.kenkern.engine.engine.NickPlayRuntime
import com.kenkern.engine.engine.PlayState
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import kotlin.math.abs
import kotlin.math.sqrt

/** Controles do motor expostos ao editor (Play/Pause/Stop + limites do aparelho). */
interface EngineControls {
    fun setPlayState(state: PlayState)
    fun currentPlayState(): PlayState

    /** GL_MAX_TEXTURE_SIZE real (0 = GL ainda não inicializou). */
    val maxTextureSize: Int
}

/**
 * SNAPSHOT DE DEBUG compartilhado GL (escreve) <-> UI/overlay (lê), sobre
 * seleção por toque e gizmo. Publicado a cada frame quando o overlay está
 * ligado (botão de inseto na TopBar). Só arrays primitivos pré-alocados e
 * campos @Volatile — ZERO alocação no thread GL. A leitura sem lock pode
 * rasgar UM frame — aceitável para diagnóstico (não interfere no jogo/editor).
 *
 * Duas referências de tamanho, EXPLÍCITAS:
 *  - px da VIEW: layout do AndroidView (o espaço do MotionEvent e do overlay);
 *  - px do FRAMEBUFFER: surface real do GL (afetada pela escala de resolução).
 * O toque é normalizado pela VIEW (nunca pelo framebuffer reduzido) e as alças
 * publicadas em px de framebuffer; o overlay converte buffer->view para desenhar.
 */
class ViewportDebugState {
    /** Overlay ligado (botão de inseto na TopBar). O GL só publica quando true. */
    @Volatile var enabled: Boolean = false

    /** Densidade da tela (px <-> dp nas mensagens). Escrito 1x na criação da view. */
    @Volatile var density: Float = 1f

    // ---- Toque (thread UI) ---- coordenadas em px da VIEW ----
    @Volatile var touchDownX = -1f
    @Volatile var touchDownY = -1f
    @Volatile var touchX = -1f
    @Volatile var touchY = -1f
    @Volatile var touchMovedPx = 0f
    @Volatile var touchElapsedMs = 0L
    @Volatile var touchClass: TouchClass = TouchClass.NONE
    @Volatile var touchReason: String = "sem toque ainda"

    // ---- Render (thread GL, por frame) ---- tamanho em px do FRAMEBUFFER ----
    @Volatile var renderW = 0
    @Volatile var renderH = 0

    // ---- Picking (thread GL, no frame em que o pick rodou) ----
    val pickRayOrigin = FloatArray(3)
    val pickRayDir = FloatArray(3)
    @Volatile var pickName: String? = null
    @Volatile var pickT = -1f            // distância ao longo do raio até a AABB (m)
    @Volatile var pickScreenDistPx = -1f // distância do toque à AABB projetada (px da view)
    @Volatile var pickTolPx = 0f         // tolerância de proximidade (px da view)
    @Volatile var pickCount = 0          // nicks testados no último pick

    // ---- Alças do gizmo (thread GL, por frame) ---- px do FRAMEBUFFER ----
    val handleX = FloatArray(TransformGizmo.DEBUG_HANDLE_COUNT)
    val handleY = FloatArray(TransformGizmo.DEBUG_HANDLE_COUNT)
    val handleValid = BooleanArray(TransformGizmo.DEBUG_HANDLE_COUNT)
    @Volatile var handleVersion = 0L

    // ---- Hit-test do gizmo (thread UI, no ACTION_DOWN) ----
    @Volatile var gizmoHit: GizmoHandle = GizmoHandle.NONE
    @Volatile var gizmoNearestDp = -1f
    @Volatile var gizmoNote: String? = null

    // ---- Pinça/câmera (thread UI, a cada evento de toque) ---- diagnóstico
    // do zoom orbital preso: nº de dedos, span inicial/atual da pinça, fator
    // do último MOVE e o motivo do último reset de baseline.
    @Volatile var pinchFingers = 0
    @Volatile var pinchStartSpan = 0f  // span quando o 2º dedo pousou (px da view)
    @Volatile var pinchSpan = 0f       // span corrente entre os dedos (px da view)
    @Volatile var pinchLastFactor = 1f // oldDist/newDist do último MOVE da pinça
    @Volatile var pinchResetReason: String = TouchController.RESET_NONE

    // ---- Voo (thread UI) ---- gesto de olhar (1 ou 2 dedos)
    @Volatile var flightGesture: String = "—"
    // ETAPA A do yaw morto no voo (v0.1.12): dx/dy em dp do ÚLTIMO MOVE de
    // olhar no voo (1 dedo: px/density; 2 dedos: dp do ponto médio). Se o campo
    // yaw muda mas estes deltas ficam 0, o gesto não chega ao rotateFlight;
    // se chegam e o yaw não gira, a base é que não o usa. Primitivos @Volatile
    // — ZERO alocação por evento.
    @Volatile var flightLookDxDp: Float = 0f
    @Volatile var flightLookDyDp: Float = 0f

    fun clearHandles() {
        for (i in handleValid.indices) handleValid[i] = false
        handleVersion++
    }

    fun bumpHandles() {
        handleVersion++
    }
}

// ---- Publicadores do estado de debug (chamados do thread de UI, sem alocação relevante) ----

fun ViewportDebugState.publishDown(x: Float, y: Float, gizmoCaptured: Boolean) {
    touchDownX = x; touchDownY = y
    touchX = x; touchY = y
    touchMovedPx = 0f
    touchElapsedMs = 0L
    touchClass = if (gizmoCaptured) TouchClass.GIZMO else TouchClass.PENDING
    touchReason = if (gizmoCaptured) "alça capturada no DOWN — gizmo tem a posse do gesto" else "DOWN — aguardando soltar"
}

fun ViewportDebugState.publishMove(x: Float, y: Float, gizmoCaptured: Boolean, moved: Boolean, elapsedMs: Long) {
    touchX = x; touchY = y
    val dx = x - touchDownX
    val dy = y - touchDownY
    touchMovedPx = sqrt(dx * dx + dy * dy)
    touchElapsedMs = elapsedMs
    when {
        gizmoCaptured -> {
            touchClass = TouchClass.GIZMO
            touchReason = "arrastando alça do gizmo"
        }
        moved -> {
            touchClass = TouchClass.DRAG
            touchReason = "arrastando câmera"
        }
        else -> {
            touchClass = TouchClass.PENDING
            touchReason = "jitter sob o slop — ainda pode virar tap"
        }
    }
}

fun ViewportDebugState.publishUp(cls: TouchClass, reason: String, elapsedMs: Long, movedPx: Float) {
    touchClass = cls
    touchReason = reason
    touchElapsedMs = elapsedMs
    touchMovedPx = movedPx
}

fun ViewportDebugState.publishClass(cls: TouchClass, reason: String) {
    touchClass = cls
    touchReason = reason
}

fun ViewportDebugState.publishPickRay(origin: Vec3, dir: Vec3) {
    pickRayOrigin[0] = origin.x; pickRayOrigin[1] = origin.y; pickRayOrigin[2] = origin.z
    pickRayDir[0] = dir.x; pickRayDir[1] = dir.y; pickRayDir[2] = dir.z
}

fun ViewportDebugState.publishPickResult(name: String?, t: Float, screenDistPx: Float, tolPx: Float, tested: Int) {
    pickName = name
    pickT = t
    pickScreenDistPx = screenDistPx
    pickTolPx = tolPx
    pickCount = tested
}

fun ViewportDebugState.publishGizmoHit(handle: GizmoHandle, nearestDp: Float, note: String? = null) {
    gizmoHit = handle
    gizmoNearestDp = nearestDp
    gizmoNote = note
}

/** Estado da pinça para a linha de diagnóstico da câmera (só primitivos). */
fun ViewportDebugState.publishPinch(
    fingers: Int,
    startSpan: Float,
    currentSpan: Float,
    lastFactor: Float,
    resetReason: String
) {
    pinchFingers = fingers
    pinchStartSpan = startSpan
    pinchSpan = currentSpan
    pinchLastFactor = lastFactor
    pinchResetReason = resetReason
}

/** Gesto de olhar do modo VOO + dx/dy (dp) do último MOVE para o diagnóstico (sem alocação). */
fun ViewportDebugState.publishFlight(gesture: String, dxDp: Float, dyDp: Float) {
    flightGesture = gesture
    flightLookDxDp = dxDp
    flightLookDyDp = dyDp
}

/**
 * GLSurfaceView da KENKERN: embutido no editor via AndroidView.
 * Loop próprio com timestep fixo; câmera orbital por toque.
 *
 * SELEÇÃO E GIZMOS (Fase 1):
 *  - tap curto (< 250 ms, < 12dp — nunca px crus; mínimo = scaledTouchSlop do
 *    sistema) => picking no ACTION_UP (raio da câmera × AABB de cada Nick, no
 *    thread GL, com as matrizes de mundo recém-atualizadas; tolerância extra de
 *    24dp da AABB projetada); resultado via [onNickPicked]. Arrasto SEM alça
 *    move só a câmera (nunca muda seleção/transform).
 *  - duplo toque => foco: no vazio enquadra a CENA toda; sobre seleção, o Nick.
 *  - botão FOCAR (F) => enquadra o Nick selecionado ([focusSelected]).
 *  - toque sobre alça do gizmo => hit-test no ACTION_DOWN (28dp) ANTES da
 *    câmera; se acertou, a câmera ignora o gesto inteiro até o ACTION_UP
 *    (requestDisallowInterceptTouchEvent); ao soltar, [onGizmoDragEnd] entrega
 *    o snapshot inicial para UM único Command de undo.
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

    /**
     * Snapshot de diagnóstico publicado pelo thread GL / UI e lido pelo overlay
     * de debug (botão de inseto na TopBar). NUNCA usado pela lógica do editor —
     * só exibição.
     */
    val debugState = ViewportDebugState()

    /** True = construção do gizmo falhou e caiu no modo VAZIO (aviso na StatusBar). */
    @Volatile
    var isGizmoDegraded: Boolean = false
        private set

    /**
     * E5 (v0.1.18): botão 'Mostrar auxiliares' da TopBar (padrão LIGADO) —
     * ícones/formas dos Nicks SEM malha (luz/câmera/área/ícone) no viewport.
     * Escrito pela UI, lido pelo thread GL. Em Play os auxiliares somem.
     */
    @Volatile
    var mostrarAuxiliares: Boolean = true

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

    /**
     * Limiar de tap em DP, nunca em px crus: 12dp mínimo especificado, ou o
     * scaledTouchSlop do sistema quando maior (dedo tremendo não desqualifica
     * mais o tap — 10px crus em tela densa eram quase nada).
     */
    private val tapSlopPx = maxOf(
        12f * density,
        ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    )

    private val touch = TouchController(
        camera,
        tapSlopPx = tapSlopPx,
        density = density,
        onTap = { x, y -> requestPick(x, y) },
        onDoubleTap = { _, _ -> handleDoubleTap() },
        gizmoHandler = GizmoTouchHandler(),
        debug = debugState
    )

    private val glRenderer = KenkernGLRenderer()
    @Volatile private var playMovement: Long = 0L
    @Volatile private var requestedPlayState = PlayState.STOPPED

    fun setPlayMovement(x: Float, z: Float) {
        if (requestedPlayState != PlayState.PLAYING) return
        playMovement = (x.toRawBits().toLong() shl 32) or (z.toRawBits().toLong() and 0xffffffffL)
    }

    private val controls = object : EngineControls {
        override fun setPlayState(state: PlayState) {
            requestedPlayState = state
            if (state != PlayState.PLAYING) playMovement = 0L
            queueEvent {
                loop.requestPlayState(state)
                if (state == PlayState.STOPPED) glRenderer.clearPlaySession()
            }
        }

        override fun currentPlayState(): PlayState = requestedPlayState

        override val maxTextureSize: Int
            get() = glRenderer.currentMaxTextureSize()
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

    /** MALHAS desenhadas no último frame (M da StatusBar "N nicks · M malhas", E6). */
    fun rendererMalhasDesenhadas(tick: Int): Int = glRenderer.currentMalhasDesenhadas()

    /**
     * H3 (v0.1.20): preview "Ver pela câmera" — id do Nick de câmera (escrito
     * pela UI, lido pelo thread GL); null desliga o preview e devolve a câmera
     * do editor SEM alterar sua posição.
     */
    fun setPreviewCamera(id: ReferenceId?) {
        glRenderer.setPreviewCamera(id)
    }

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

    // Toque em px da VIEW (overlay + distância de tolerância em tela).
    @Volatile
    private var pickTouchX = 0f
    @Volatile
    private var pickTouchY = 0f

    // Pré-alocados para o picking (thread GL; zero alocação por tap).
    private val pickStack = arrayOfNulls<Nick>(256)
    private val pickOrigin = Vec3()
    private val pickDir = Vec3()
    private val pickProj = Mat4()
    private val pickView = Mat4()
    private val pickVp = Mat4()

    // Pré-alocados para a publicação de debug das alças (thread GL).
    private val dbgProj = Mat4()
    private val dbgView = Mat4()
    private val dbgVp = Mat4()
    private val dbgWorldPos = Vec3()
    private val dbgWorldRot = Quaternion()
    private val dbgExtractor = RotationFromMatrix()

    /**
     * Converte o toque em NDC usando a LARGURA/ALTURA DA VIEW (mesmo espaço do
     * MotionEvent). Com a escala de resolução ativa o framebuffer é menor, mas
     * o NDC é normalizado: multiplicar pela view (não pelo buffer reduzido) é
     * o que mantém o raio correto. Y invertido: NDC +1 = topo da tela.
     */
    private fun requestPick(xPx: Float, yPx: Float) {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        pickNdcX = (xPx / w) * 2f - 1f
        pickNdcY = 1f - (yPx / h) * 2f
        pickTouchX = xPx
        pickTouchY = yPx
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

    // Rascunhos do ponto de surgimento (T0.3) — sem alocação por chamada.
    private val spawnView = com.kenkern.engine.core.math.Mat4()
    private val spawnEye = Vec3()
    private val spawnOut = FloatArray(2)

    /**
     * T0.3 (adendo v4, seção 3): posição do CENTRO DA TELA projetada no CHÃO
     * (y=0) — onde o Nick novo da janela "Adicionar Nick" nasce. Escreve x/z
     * em [out] (tamanho 2) e devolve true; false quando não há interseção à
     * frente (olhando para cima/paralelo): o chamador usa o alvo da câmera.
     * Pode ser chamado de qualquer thread (usa só a câmera; valores de float).
     */
    fun centroProjetadoNoChao(out: FloatArray): Boolean {
        camera.view(spawnView)
        camera.eyePoint(spawnEye)
        // Forward da câmera = -3ª linha da view (mesma derivação do renderer).
        val fx = -spawnView.m[2]
        val fy = -spawnView.m[6]
        val fz = -spawnView.m[10]
        val t = PickingMath.interseccaoChao(spawnEye.x, spawnEye.y, spawnEye.z, fx, fy, fz, spawnOut)
        if (t >= 0f) {
            out[0] = spawnOut[0]
            out[1] = spawnOut[1]
            return true
        }
        // Fallback: projeção horizontal do ALVO da câmera (orbital: o próprio
        // alvo; voo: 8 m à frente da posição no plano XZ).
        return false
    }

    /** Fallback do ponto de surgimento: alvo da câmera (orbital) ou 8 m à frente (voo). */
    fun pontoSpawnFallback(out: FloatArray) {
        if (camera.mode == CameraMode.FLIGHT) {
            camera.view(spawnView)
            val fx = -spawnView.m[2]
            val fz = -spawnView.m[10]
            camera.eyePoint(spawnEye)
            out[0] = spawnEye.x + fx * 8f
            out[1] = spawnEye.z + fz * 8f
        } else {
            out[0] = camera.target.x
            out[1] = camera.target.z
        }
    }

    /** Duplo toque: vazio => cena toda; sobre um Nick selecionado => o Nick. */
    private fun handleDoubleTap() {
        focusAllNicks = selectionProvider() == null
        focusPending = true
    }

    init {
        debugState.density = density
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
    }.onFailure { KenkernLog.w(TAG, "detecção de GLES3 falhou — assumindo que o aparelho suporta", it) }
        .getOrDefault(true)

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event == null) return false
        if (requestedPlayState != PlayState.STOPPED) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            onFirstTouch()
        }
        // runCatching NUNCA derruba o app por um evento de toque, mas o erro
        // era engolido EM SILÊNCIO (getOrDefault(false)) — foi assim que a
        // IllegalArgumentException do "dedo morto" da pinça congelou a câmera
        // sem nenhum rastro. Agora sempre deixa uma linha no logcat.
        val handled = runCatching { touch.onTouch(event) }
            .onFailure {
                Log.e(TAG, "onTouch descartado: ${it.javaClass.simpleName}: ${it.message}", it)
                KenkernLog.e(TAG, "evento de toque descartado", it)
            }
            .getOrDefault(false)
        // Posse do gesto decidida no DOWN: se o gizmo capturou a alça, nenhum pai
        // intercepta o gesto até o ACTION_UP (o sistema limpa a flag no próximo DOWN).
        if (event.actionMasked == MotionEvent.ACTION_DOWN &&
            runCatching { touch.isGizmoCapturedNow() }
                .onFailure { KenkernLog.w(TAG, "isGizmoCapturedNow falhou — gesto pode ser interceptado", it) }
                .getOrDefault(false)
        ) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return handled
    }

    /**
     * Publica no [debugState] as posições de tela das alças do gizmo (px do
     * FRAMEBUFFER) e o tamanho do render. Roda no thread GL DEPOIS do render —
     * usa a MESMA câmera/projeção do frame que acabou de ser desenhado, então
     * os círculos verdes do overlay correspondem ao que está na tela.
     */
    private fun publishDebugFrameGL(scene: Scene?) {
        val rw = glRenderer.backendViewportW()
        val rh = glRenderer.backendViewportH()
        debugState.renderW = rw
        debugState.renderH = rh
        val sel = selectionProvider()
        val s = scene
        if (sel == null || s == null || rw < 1 || rh < 1) {
            debugState.clearHandles()
            return
        }
        val nick = s.findNick(sel)
        if (nick == null || !nick.active || !nick.visible) {
            debugState.clearHandles()
            return
        }
        val aspect = rw.toFloat() / rh.coerceAtLeast(1).toFloat()
        camera.projection(dbgProj, aspect)
        camera.view(dbgView)
        dbgVp.multiply(dbgProj, dbgView)
        val wm = nick.transform.worldMatrix
        dbgWorldPos.set(wm.m[12], wm.m[13], wm.m[14])
        extractWorldRotationTo(wm, dbgWorldRot)
        val ss = gizmo.screenScaleFor(camera, dbgWorldPos)
        gizmo.projectHandlesToScreen(
            dbgVp, rw.toFloat(), rh.toFloat(),
            dbgWorldPos, dbgWorldRot, ss,
            gizmoState.space, gizmoState.mode,
            debugState.handleX, debugState.handleY, debugState.handleValid
        )
        debugState.bumpHandles()
    }

    /** Extrai rotação de mundo de uma matriz TRS (colunas normalizadas), sem alocação. */
    private fun extractWorldRotationTo(m: Mat4, out: Quaternion) {
        val mm = m.m
        val sx = kotlin.math.sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
        val sy = kotlin.math.sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
        val sz = kotlin.math.sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            dbgExtractor.set(
                mm[0] / sx, mm[4] / sy, mm[8] / sz,
                mm[1] / sx, mm[5] / sy, mm[9] / sz,
                mm[2] / sx, mm[6] / sy, mm[10] / sz
            )
            dbgExtractor.toMatrixComponents(out)
        } else {
            out.setIdentity()
        }
    }

    // ---------- Handler de arrasto do gizmo (thread de UI, zero alocação) ----------

    private inner class GizmoTouchHandler : GizmoDragHandler {
        private val dragProj = Mat4()
        private val dragView = Mat4()
        private val dragVp = Mat4()
        private val dragWorldPos = Vec3()
        private val dragWorldRot = Quaternion()
        private val dragExtractor = RotationFromMatrix()
        private val hitNearest = FloatArray(1)
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
            val scene = sceneProvider()
            if (scene == null) {
                debugState.publishGizmoHit(GizmoHandle.NONE, -1f, "cena nula")
                return false
            }
            val sel = selectionProvider()
            if (sel == null) {
                debugState.publishGizmoHit(GizmoHandle.NONE, -1f, "sem seleção — toque no cubo primeiro")
                return false
            }
            val nick = scene.findNick(sel)
            if (nick == null || !nick.active || !nick.visible) {
                debugState.publishGizmoHit(GizmoHandle.NONE, -1f, "Nick selecionado inativo/invisível")
                return false
            }
            val w = width.toFloat()
            val h = height.toFloat()
            if (w < 1f || h < 1f) {
                debugState.publishGizmoHit(GizmoHandle.NONE, -1f, "viewport sem tamanho")
                return false
            }
            refreshMatrices(w, h)
            dragWorldPos.set(nick.transform.worldMatrix.m[12], nick.transform.worldMatrix.m[13], nick.transform.worldMatrix.m[14])
            extractRotation(nick.transform.worldMatrix, dragWorldRot)
            val ss = gizmo.screenScaleFor(camera, dragWorldPos)
            val radius = 28f * density // tolerância de hit-test: 28dp
            hitNearest[0] = -1f
            val handle = gizmo.hitTest(
                x, y, dragVp, w, h, dragWorldPos, dragWorldRot, ss,
                gizmoState.space, gizmoState.mode, radius, hitNearest
            )
            val nearestDp = if (hitNearest[0].isFinite() && hitNearest[0] >= 0f) hitNearest[0] / density else -1f
            if (handle == GizmoHandle.NONE) {
                debugState.publishGizmoHit(
                    GizmoHandle.NONE, nearestDp,
                    "nenhuma alça dentro de 28dp (alça mais próxima: ${if (nearestDp >= 0f) "%.0f".format(nearestDp) else "?"}dp)"
                )
                return false
            }
            debugState.publishGizmoHit(handle, nearestDp, null)
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

    // Scratch do picking (sem alocação por frame). O CÁLCULO mora no
    // PickingMath (fonte única compartilhada com o Diagnóstico — D1).
    private val pickMinW = Vec3()
    private val pickMaxW = Vec3()
    private val focusMinTmp = Vec3()
    private val focusMaxTmp = Vec3()

    /** Delegação ao PickingMath (fonte única do picking — D1). */
    private fun nickWorldAabb(nick: Nick, outMin: Vec3, outMax: Vec3) {
        PickingMath.worldAabb(nick, outMin, outMax)
    }

    /**
     * Picking no thread GL, em DUAS camadas:
     *  1. EXATO: raio da câmera × AABB (em mundo) de cada Nick ativo/visível —
     *     slab test sem alocação; escolhe o MENOR t (mais próximo da câmera);
     *  2. TOLERÂNCIA: se o toque caiu fora de todas as AABBs, aceita um toque a
     *     até [PICK_TOLERANCE_DP]dp da AABB PROJETADA na tela (cubo pequeno ou
     *     longe não pode ser impossível de tocar). Empate de distância de tela
     *     vale o menor t.
     * REGRA "CENA = TELA" (Bug A): só Nicks DESSENHAVEIS (malha importada ou
     * folha) são pegáveis — containers (raiz "Mundo") não aparecem no viewport
     * e portanto não podem ser selecionados por toque.
     * O toque é normalizado pela VIEW (requestPick) e comparado em px da view:
     * os cantos projetados (px do framebuffer) são convertidos por bufToView.
     * Publica raio e resultado no [debugState] (overlay de diagnóstico).
     */
    private fun pickNickAt(
        scene: Scene,
        aspect: Float,
        viewProj: Mat4,
        renderW: Int,
        renderH: Int
    ): ReferenceId? {
        camera.screenRay(pickNdcX, pickNdcY, aspect, pickOrigin, pickDir)
        debugState.publishPickRay(pickOrigin, pickDir)

        val bufToView = if (renderW > 0) width.toFloat() / renderW else 1f
        val tolPx = PICK_TOLERANCE_DP * density

        pickStack.fill(null)
        var top = 0
        for (r in scene.roots.indices) {
            if (top >= pickStack.size) break
            pickStack[top++] = scene.roots[r]
        }
        var bestId: ReferenceId? = null
        var bestScreen = Float.MAX_VALUE
        var bestT = Float.MAX_VALUE
        var nearestOverall = Float.MAX_VALUE // AABB projetada mais próxima, mesmo fora da tolerância
        var tested = 0

        // E5: pontuação de UMA AABB de toque contra o raio do toque — reutilizada
        // por malhas (AABB do .KERN/cubo) e por AUXILIARES DE EDITOR (caixa 0,4 m
        // na posição do Nick, mesma tolerância de 24dp).
        fun ofertar(id: com.kenkern.engine.core.ReferenceId, cx: Float, cy: Float, cz: Float, ex: Float, ey: Float, ez: Float) {
            val t = PickingMath.rayAabb(pickOrigin, pickDir, cx, cy, cz, ex, ey, ez)
            val sd = if (t >= 0f) {
                0f // hit exato: o toque está DENTRO da AABB projetada
            } else {
                PickingMath.projectedAabbDistance(
                    viewProj, renderW.toFloat(), renderH.toFloat(), bufToView,
                    pickTouchX, pickTouchY, cx, cy, cz, ex, ey, ez
                )
            }
            if (sd >= 0f && sd < nearestOverall) nearestOverall = sd
            if (sd in 0f..tolPx) {
                val better = sd < bestScreen - 0.5f ||
                    (sd <= bestScreen + 0.5f && t >= 0f && t < bestT)
                if (better) {
                    bestScreen = sd
                    bestT = if (t >= 0f) t else -1f
                    bestId = id
                }
            }
        }

        while (top > 0) {
            val nick = pickStack[--top] ?: continue
            // REGRA "CENA = TELA": container sem malha não é desenhado — não é
            // pegável por toque (senão o tap selecionaria objetos invisíveis).
            if (nick.active && nick.visible && MalhaNick.desenhavel(nick)) {
                tested++
                // AABB em MUNDO REAL (malha importada: AABB do .KERN transformada;
                // Nick padrão: cubo de 1 m) — 8 cantos × matriz de mundo.
                nickWorldAabb(nick, pickMinW, pickMaxW)
                ofertar(
                    nick.id,
                    (pickMinW.x + pickMaxW.x) * 0.5f,
                    (pickMinW.y + pickMaxW.y) * 0.5f,
                    (pickMinW.z + pickMaxW.z) * 0.5f,
                    (pickMaxW.x - pickMinW.x) * 0.5f,
                    (pickMaxW.y - pickMinW.y) * 0.5f,
                    (pickMaxW.z - pickMinW.z) * 0.5f
                )
            } else if (nick.active && nick.visible &&
                com.kenkern.engine.engine.AuxiliaresPreferencia.devePegar(mostrarAuxiliares, loop.playState) &&
                MalhaNick.ehAuxiliar(nick)
            ) {
                // E5 + H1 (v0.1.20): AUXILIAR DE EDITOR — pegável por toque (mesma
                // tolerância de 24dp) com caixa de toque de 0,4 m na posição do Nick.
                // CAUSA RAIZ do H1 estava AQUI (linha 784 do E5): a condição usava
                // `playState != STOPPED` — o INVERSO da regra ("pegáveis fora de
                // Play"): depois do Stop os auxiliares NÃO voltavam a ser pegáveis
                // no editor e em Play seriam pegáveis invisíveis. Agora a decisão
                // vem de AuxiliaresPreferencia (preference do usuário nunca muda;
                // Play/Pausa só suprimem — mesma regra do desenho).
                tested++
                val wm = nick.transform.worldMatrix
                val e = EditorHelpers.AABB_TOQUE
                ofertar(nick.id, wm.m[12], wm.m[13], wm.m[14], e, e, e)
            }
            val children = nick.children
            for (ci in children.indices) {
                if (top >= pickStack.size) break
                pickStack[top++] = children[ci]
            }
        }
        // T0.2: nome de diagnóstico do picking = texto do usuário OU a chave do
        // tipo (overlay técnico — o rótulo localizado mora na UI, não no render).
        val name = bestId?.let { id -> scene.findNick(id)?.let { it.name ?: it.kind } }
        val screenOut = when {
            bestId != null && bestScreen.isFinite() -> bestScreen
            nearestOverall.isFinite() -> nearestOverall // nada dentro da tolerância: mostra a mais próxima
            else -> -1f
        }
        debugState.publishPickResult(
            name,
            if (bestId != null && bestT.isFinite()) bestT else -1f,
            screenOut,
            tolPx,
            tested
        )
        return bestId
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
            // REGRA "CENA = TELA": o foco "tudo" enquadra o que está DESENHADO —
            // containers não desenhados não esticam a AABB da cena (Bug A).
            if (nick.active && nick.visible && MalhaNick.desenhavel(nick) && (focusAllNicks || nick.id == selected)) {
                // AABB em MUNDO REAL (malha importada incluída — foco enquadra o
                // modelo inteiro, não só o cubo de 1 m).
                nickWorldAabb(nick, focusMinTmp, focusMaxTmp)
                if (!hasBox) {
                    focusMin.set(focusMinTmp)
                    focusMax.set(focusMaxTmp)
                    hasBox = true
                } else {
                    focusMin.set(
                        minOf(focusMin.x, focusMinTmp.x), minOf(focusMin.y, focusMinTmp.y), minOf(focusMin.z, focusMinTmp.z)
                    )
                    focusMax.set(
                        maxOf(focusMax.x, focusMaxTmp.x), maxOf(focusMax.y, focusMaxTmp.y), maxOf(focusMax.z, focusMaxTmp.z)
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
            }.onFailure { KenkernLog.w(TAG, "eglDestroyContext falhou — contexto EGL pode vazar", it) }
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
            for ((idx, attr) in candidates.withIndex()) {
                val num = IntArray(1)
                val configs = arrayOfNulls<EGLConfig>(1)
                val ok = runCatching {
                    egl.eglChooseConfig(display, attr, configs, 1, num) && num[0] > 0 && configs[0] != null
                }.onFailure {
                    // Cada candidato recusado é registrado; o final lança mensagem clara.
                    KenkernLog.w(TAG, "candidato EGL $idx recusado pelo driver", it)
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

        fun currentMaxTextureSize(): Int = backend.maxTextureSize

        fun currentUpdateMs(): Float = engineRenderer.updateMs

        fun currentDrawMs(): Float = engineRenderer.drawMs

        fun currentDrawCalls(): Int = engineRenderer.drawCount

        fun currentMalhasDesenhadas(): Int = engineRenderer.malhasDesenhadas

        fun isKernRoundtripOk(): Boolean = engineRenderer.isKernRoundtripOk()

        /** Tamanho do framebuffer real (pode diferir da view com escala de resolução). */
        fun backendViewportW(): Int = backend.viewportWidth

        fun backendViewportH(): Int = backend.viewportHeight

        override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: EGLConfig?) {
            // Marcador de progresso: EGL criou a surface (o trecho entre "EditorScreen
            // composto" e esta linha cobre eglCreateContext/eglCreateWindowSurface,
            // janela típica de crash NATIVO em driver GL).
            BootTrace.mark("surface criada")
            surfaceReady = false
            isEngineHealthy = false
            runCatching {
                // Contexto novo = handles de GPU antigos inválidos: as malhas
                // importadas voltam a subir pela fila pendente (nada de handle órfão).
                AssetMeshLibrary.limpar()
                backend.destroy() // libera restos de contexto anterior
            }.onFailure { KenkernLog.w(TAG, "destroy() do backend anterior falhou", it) }
            runCatching {
                engineRenderer.initialize(backend, gizmo)
                surfaceReady = true
                isEngineHealthy = true
                Log.i(TAG, "Viewport GL inicializado (${backend.name})")
            }.onFailure {
                Log.e(TAG, "Falha na inicialização GL (modo seguro)", it)
                KenkernLog.e(TAG, "Inicialização GL falhou — viewport em modo seguro", it)
            }
        }

        override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
            runCatching { backend.resize(width, height) }
                .onFailure { KenkernLog.e(TAG, "resize($width, $height) falhou — viewport pode ficar com tamanho errado", it) }
        }

        private var editorScene: Scene? = null
        private var playScene: Scene? = null
        private var playRuntime: NickPlayRuntime? = null
        private var playCamera: NickPlayCamera? = null
        // M1.1 (v0.1.21): scripts Lua ligados aos Nicks da cópia de Play.
        private var playScripts: NickLuaScripts? = null

        // H3 (v0.1.20): preview "Ver pela câmera" (editor) — id do Nick de
        // câmera escolhido (selecionado ou o principal); null = desligado.
        // Escrito pela UI, lido pelo thread GL.
        @Volatile
        var previewLenteId: com.kenkern.engine.core.ReferenceId? = null
        private val lenteProj = Mat4()
        private val lenteView = Mat4()
        private val lenteValores = com.kenkern.engine.core.LenteNick.Valores()

        /** H3: aviso "sem câmera" UMA VEZ por sessão de Play (regra da tarefa). */
        private var avisoSemCameraFeito = false

        fun setPreviewCamera(id: com.kenkern.engine.core.ReferenceId?) {
            previewLenteId = id
        }

        fun clearPlaySession() {
            editorScene = null
            playScene = null
            playRuntime = null
            playCamera = null
            playScripts = null
            avisoSemCameraFeito = false
        }

        private fun sceneForFrame(source: Scene?): Scene? {
            if (loop.playState == PlayState.STOPPED || source == null) {
                clearPlaySession()
                return source
            }
            if (editorScene !== source) {
                editorScene = source
                // Uma cópia por sessão; Play nunca altera a cena editável ou o autosave.
                val copy = CkFormat.parseToNewScene(CkFormat.writeToString(source), source.name)
                playScene = copy
                playRuntime = copy?.let { NickPlayRuntime(it) }
                copy?.updateWorldTransforms()
                playCamera = copy?.let { c ->
                    playRuntime?.let { r -> NickPlayCamera(c, r.player, r.followCamera, r.basicCamera) }
                }
                // M1.1: ponte Lua/nick — 1 runtime por Nick com classe "Script".
                playScripts = copy?.let { c -> NickLuaScripts(c).also { s -> s.iniciarTodos() } }
            }
            return playScene
        }

        /**
         * H3 (v0.1.20): caminho de CÂMERA do frame —
         *  1. PREVIEW "Ver pela câmera" (só no editor): render pela matriz do
         *     Nick de câmera (fov/near/far/orto); a câmera do editor NÃO muda —
         *     sair do preview volta exatamente onde estava;
         *  2. PLAY pela CÂMERA PRINCIPAL (matriz própria do Nick);
         *  3. Play pela câmera do runtime (follow/fixa — M1);
         *  4. Play sem NENHUMA câmera: câmera do editor + AVISO UMA VEZ.
         */
        private fun desenharFrame(scene: Scene?, gameCamera: NickPlayCamera?) {
            val s = scene
            val emJogo = engineRenderer.emJogo
            val rw = backend.viewportWidth.coerceAtLeast(1)
            val rh = backend.viewportHeight.coerceAtLeast(1)
            val aspect = rw.toFloat() / rh.toFloat()

            val previewId = previewLenteId
            if (!emJogo && previewId != null && s != null) {
                val nick = s.findNick(previewId)
                if (nick != null && nick.kind.startsWith("camera.")) {
                    com.kenkern.engine.core.LenteNick.ler(nick, lenteValores)
                    val fov = LenteNickMatriz.projecaoDe(lenteValores, aspect, lenteProj)
                    LenteNickMatriz.view(nick, lenteView)
                    val wm = nick.transform.worldMatrix.m
                    engineRenderer.renderFrameComMatrizes(
                        backend, s, lenteProj, lenteView,
                        wm[12], wm[13], wm[14], fov,
                        loop.engineTime, selectionProvider(), gizmoState
                    )
                    return
                }
            }

            if (gameCamera?.active == true) {
                if (gameCamera.usaMatrizPropria) {
                    // CÂMERA PRINCIPAL (H3): fov/near/far/ortográfica do Nick.
                    gameCamera.prepararProjecaoPropria(aspect)
                    val viewP = gameCamera.viewPropria.m
                    engineRenderer.renderFrameComMatrizes(
                        backend, s, gameCamera.projPropria, gameCamera.viewPropria,
                        viewP[12], viewP[13], viewP[14], gameCamera.fovPropria,
                        loop.engineTime, null, gizmoState
                    )
                } else {
                    engineRenderer.renderFrame(
                        backend, s, gameCamera.camera,
                        loop.engineTime, if (emJogo) null else selectionProvider(), gizmoState
                    )
                }
                return
            }

            // Play sem NENHUMA câmera: câmera do editor + aviso UMA VEZ (H3).
            if (emJogo && !avisoSemCameraFeito) {
                avisoSemCameraFeito = true
                KenkernLog.w(
                    TAG,
                    "Play sem Nick de câmera (nenhuma principal/follow/fixa) — usando a câmera do editor"
                )
            }
            engineRenderer.renderFrame(
                backend, s, camera,
                loop.engineTime, if (emJogo) null else selectionProvider(), gizmoState
            )
        }

        override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
            try {
                val scene = sceneForFrame(sceneProvider())
                // Instrumentação: ms de update (timestep fixo + world transforms).
                val updateStart = System.nanoTime()
                loop.tick(System.nanoTime()) { dt ->
                    scene?.updateFunctions(dt, loop.engineTime)
                    val move = playMovement
                    playRuntime?.step(dt, Float.fromBits((move ushr 32).toInt()), Float.fromBits(move.toInt()))
                    // M1.1: on_update(dt) dos scripts Lua (erro desativa o script,
                    // não o jogo; orçamento de tempo + limite de instruções).
                    playScripts?.atualizar(dt)
                }
                // Matrizes de mundo sempre atualizadas (mesmo pausado, o Inspetor edita transform).
                scene?.updateWorldTransforms()
                val gameCamera = playCamera
                gameCamera?.update()
                engineRenderer.reportUpdateMs((System.nanoTime() - updateStart) * 1e-6f)
                if (surfaceReady) {
                    // E5: estado dos AUXILIARES DE EDITOR lido por frame (thread GL):
                    // visíveis no editor (padrão) e SUMEM em Play (nunca no jogo exportado).
                    engineRenderer.mostrarAuxiliares = mostrarAuxiliares
                    engineRenderer.emJogo = loop.playState != com.kenkern.engine.engine.PlayState.STOPPED
                    // Picking pendente: roda com as matrizes de mundo recém-atualizadas.
                    if (pickPending) {
                        pickPending = false
                        val s = scene
                        if (!engineRenderer.emJogo && s != null && s.roots.isNotEmpty()) {
                            val rw = backend.viewportWidth
                            val rh = backend.viewportHeight.coerceAtLeast(1)
                            val aspect = rw.toFloat() / rh.toFloat()
                            // Mesma projeção usada no render deste frame: o raio do
                            // picking usa o MESMO aspect/FOV da matriz de projeção.
                            camera.projection(pickProj, aspect)
                            camera.view(pickView)
                            pickVp.multiply(pickProj, pickView)
                            val picked = runCatching {
                                pickNickAt(s, aspect, pickVp, rw, rh)
                            }.onFailure {
                                // Tap "morrendo" sem rastro era o pior bug de UX possível
                                // de diagnosticar (regra nova: registra sempre).
                                KenkernLog.e(TAG, "picking falhou — tap não seleciona", it)
                            }.getOrNull()
                            mainHandler.post { onNickPicked(picked) }
                        }
                    }
                    // FOCO pendente (botão F / duplo toque): AABB e ajuste da câmera.
                    if (focusPending) {
                        focusPending = false
                        val s = scene
                        if (!engineRenderer.emJogo && s != null) runCatching { applyFocus(s) }
                            .onFailure { KenkernLog.w(TAG, "foco de câmera falhou", it) }
                    }
                    desenharFrame(scene, gameCamera)
                    // Publicação de debug (overlay de toque): alças em px de
                    // framebuffer + tamanho do render. Só quando o overlay está ON.
                    if (debugState.enabled) {
                        runCatching { publishDebugFrameGL(scene) }
                            .onFailure { KenkernLog.w(TAG, "publicação do overlay de debug falhou", it) }
                    }
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
                KenkernLog.e(TAG, "Frame descartado", e)
                runCatching {
                    backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
                    backend.endFrame()
                }.onFailure { KenkernLog.e(TAG, "clear emergencial do frame também falhou", it) }
            }
        }
    }

    companion object {
        private const val TAG = "Kenkern/SurfaceView"

        /** Tolerância de proximidade do picking: toque a até 24dp da AABB projetada. */
        private const val PICK_TOLERANCE_DP = 24f
    }
}
