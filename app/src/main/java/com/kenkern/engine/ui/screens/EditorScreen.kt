package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.zIndex
import com.kenkern.engine.ui.jogo.AlvoToque
import com.kenkern.engine.ui.components.KButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.editor.PanelUiState
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.core.Nick
import com.kenkern.engine.render.CameraMode
import com.kenkern.engine.render.GizmoHandle
import com.kenkern.engine.render.GizmoMode
import com.kenkern.engine.render.GizmoSpace
import com.kenkern.engine.render.KenkernSurfaceView
import com.kenkern.engine.render.TransformGizmo
import java.util.Locale
import com.kenkern.engine.ui.nickDisplayName
import com.kenkern.engine.ui.panels.AddNickPanel
import com.kenkern.engine.ui.panels.DockedPanel
import com.kenkern.engine.ui.panels.FloatingPanel
import com.kenkern.engine.ui.panels.HierarchyPanel
import com.kenkern.engine.ui.panels.InspectorPanel
import com.kenkern.engine.ui.panels.PanelResizeDivider
import com.kenkern.engine.R
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernSuccess
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernSingCobre
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

private const val TAG_UI = "Kenkern/EditorScreen"

/**
 * Estado por sessão do app (processo): a dica de gestos aparece uma única vez
 * e NÃO volta após o primeiro toque no viewport, nem ao reabrir o editor.
 */
object ViewportSession {
    @Volatile
    var gestureHintDismissed: Boolean = false
}

/**
 * Tela do EDITOR — estrutura fixa de ferramenta profissional.
 *
 *   Column raiz (NADA sobrepõe o viewport):
 *     ┌ TopBar 48dp ──── Voltar | Undo/Redo | Play/Pause/Stop | resolução | painéis
 *     ├ Row central ──── Hierarquia (acoplada) | Viewport | Inspetor (acoplado)
 *     └ StatusBar 24dp ─ estado, FPS, integridade GL/.KERN
 *
 *  - Painéis ACOPLADOS por padrão: Hierarquia à esquerda (200dp, mín 140, máx 320),
 *    Inspetor à direita (240dp, mín 180, máx 360); divisores arrastáveis de 6dp.
 *  - Modo flutuante é exceção, apenas pelo menu do painel.
 *  - Telas estreitas (<600dp): acoplar deixaria o viewport sem área útil, então
 *    o painel "acoplado" é apresentado como overlay de lateral no viewport.
 *  - Grade de espaçamento 4/8/12/16dp; texto mínimo 12sp; alvo de toque ≥ 40dp.
 */
@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
    // Marcador de progresso (diagnóstico de crash nativo): esta tela COMPOSTOU.
    // remember = grava 1x por composição, não a cada recomposição.
    remember { BootTrace.mark("EditorScreen composto") }
    var surfaceView by remember { mutableStateOf<KenkernSurfaceView?>(null) }
    val sceneRevision = vm.revision.intValue
    val hasActivePlayer = remember(vm.scene.value, sceneRevision) {
        fun Nick.hasActivePlayerInSubtree(ancestorsActive: Boolean): Boolean {
            val activePath = ancestorsActive && active
            if (activePath && kind == "body.player") return true
            return children.any { it.hasActivePlayerInSubtree(activePath) }
        }
        vm.scene.value?.roots?.any { it.hasActivePlayerInSubtree(true) } == true
    }
    var resolutionScale by remember { mutableStateOf(1f) }
    var fpsTick by remember { mutableStateOf(0) }
    var hintVisible by remember { mutableStateOf(!ViewportSession.gestureHintDismissed) }
    val density = LocalDensity.current

    // Overlay de diagnóstico de toque (botão de inseto na TopBar):
    // texto ao vivo + ponto vermelho no toque + círculos verdes nas alças.
    var debugOverlay by rememberSaveable { mutableStateOf(false) }

    // Modo de transformação (gizmo): Mover/Girar/Escalar + Local/Global + Snap
    var gizmoMode by rememberSaveable { mutableStateOf(GizmoMode.MOVE) }
    var gizmoSpace by rememberSaveable { mutableStateOf(GizmoSpace.GLOBAL) }
    var snapEnabled by rememberSaveable { mutableStateOf(false) }

    // Modo da câmera: Orbital ou Voo
    var cameraMode by rememberSaveable { mutableStateOf(CameraMode.ORBITAL) }

    // E5: Mostrar auxiliares de editor (ícones de luz/câmera/área no viewport).
    var mostrarAuxiliares by rememberSaveable { mutableStateOf(true) }
    val alternarAuxiliares: (Boolean) -> Unit = { ligado ->
        mostrarAuxiliares = ligado
        surfaceView?.mostrarAuxiliares = ligado
    }

    // H3 (v0.1.20): "Ver pela câmera" — preview pela câmera do Nick SELECIONADO
    // (camera.*) ou da CÂMERA PRINCIPAL; sair volta à câmera do editor sem
    // perder a posição (a OrbitCamera não é tocada durante o preview).
    var verPelaCamera by rememberSaveable { mutableStateOf(false) }
    val cameraAlvoPreview: Nick? = remember(vm.scene.value, sceneRevision, vm.selection.value) {
        val s = vm.scene.value
        if (s == null) null else {
            val sel = vm.selection.value?.let { s.findNick(it) }
            when {
                sel != null && sel.kind.startsWith("camera.") -> sel
                else -> {
                    var achou: Nick? = null
                    val pilha = ArrayList<Nick>(16)
                    for (r in s.roots) pilha.add(r)
                    var head = 0
                    while (head < pilha.size && achou == null) {
                        val n2 = pilha[head++]
                        if (n2.active && n2.kind.startsWith("camera.")) {
                            val v = com.kenkern.engine.core.LenteNick.Valores()
                            com.kenkern.engine.core.LenteNick.ler(n2, v)
                            if (v.principal) achou = n2
                        }
                        for (c in n2.children) pilha.add(c)
                    }
                    achou
                }
            }
        }
    }
    LaunchedEffect(verPelaCamera, cameraAlvoPreview, vm.playState.value) {
        surfaceView?.setPreviewCamera(
            if (verPelaCamera && vm.playState.value == PlayState.STOPPED) cameraAlvoPreview?.id else null
        )
    }

    // E4: orientação atual — o layout dos painéis (posição/tamanho flutuantes)
    // é salvo POR ORIENTAÇÃO: retrato e paisagem têm viewports diferentes.
    val orientacao = if (LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    ) "land" else "port"

    // ---------- Janela "Adicionar Nick" (T0.3): painel lateral no viewport ----------
    var addNickVisivel by rememberSaveable { mutableStateOf(false) }
    // Ponto de surgimento (centro da tela projetado no chão) — calculado ao ABRIR.
    val spawnXz = remember { FloatArray(2) }
    val abrirAddNick = {
        val sv = surfaceView
        if (sv != null && !sv.centroProjetadoNoChao(spawnXz)) {
            sv.pontoSpawnFallback(spawnXz)
        }
        addNickVisivel = true
    }

    // ---------- Importação de modelo (ETAPA A): SAF + opções ----------
    var uriImportacaoPendente by remember { mutableStateOf<android.net.Uri?>(null) }
    var pastaImportacaoPendente by rememberSaveable { mutableStateOf(false) }
    var opcoesImportacaoVisiveis by rememberSaveable { mutableStateOf(false) }
    val importarArquivoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            uriImportacaoPendente = uri
            pastaImportacaoPendente = false
            opcoesImportacaoVisiveis = true
        }
    }
    val importarPastaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            uriImportacaoPendente = uri
            pastaImportacaoPendente = true
            opcoesImportacaoVisiveis = true
        }
    }
    val onImportClick: (Boolean) -> Unit = { pasta ->
        if (pasta) {
            importarPastaLauncher.launch(null)
        } else {
            // .fbx/.glb não têm MIME universal: octet-stream cobre os binários.
            importarArquivoLauncher.launch(
                arrayOf(
                    "application/octet-stream", "model/gltf-binary", "model/gltf+json",
                    "application/zip", "application/x-zip-compressed"
                )
            )
        }
    }

    val hierarchy = remember(orientacao) {
        vm.panelStore.get("hierarchy", orientacao, "Hierarquia", 200f, 140f, 320f, 280f, 340f)
    }
    val inspector = remember(orientacao) {
        vm.panelStore.get("inspector", orientacao, "Inspetor", 240f, 180f, 360f, 300f, 400f)
    }
    val persistHierarchy = remember { { vm.panelStore.persist(hierarchy) } }
    val persistInspector = remember { { vm.panelStore.persist(inspector) } }

    // E4: quem está na frente — tocar em um painel flutuante o traz para o
    // primeiro plano (zIndex); Resetar layout devolve o padrão profissional.
    var painelFoco by remember { mutableStateOf<String?>(null) }
    val resetarLayout = {
        vm.panelStore.resetar(hierarchy)
        vm.panelStore.resetar(inspector)
        painelFoco = null
    }

    // S6 (v0.1.24): JANELA DE JOGO — Play abre TELA CHEIA só do jogo: a
    // TopBar/painéis/StatusBar do editor SOMEM (o AndroidView do viewport
    // PERMANECE no mesmo ponto da árvore — a superfície GL NÃO é recriada,
    // só redimensionada), os toques de câmera do editor já são engolidos em
    // Play (KenkernSurfaceView.onTouchEvent) e a seleção/gizmos já não
    // desenham em jogo (renderer). Pausa/Sair ficam na mão do usuário.
    val janelaDeJogo = vm.playState.value != PlayState.STOPPED

    // ---- P1 (v0.1.25): roteamento de toque da janela de jogo ----
    // Coordenadas do Box do viewport (== espaço px da SurfaceView) e regiões
    // dos botões, publicadas à SurfaceView quando o layout muda (NUNCA por frame).
    val coordsDoViewport = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val regioesJogo = remember { HashMap<AlvoToque, Rect>() }
    val publicarRegioes = {
        val box = coordsDoViewport.value
        val sv = surfaceView
        if (box != null && sv != null) {
            sv.definirRegioesReservadas(
                regioesJogo.map { (alvo, r) ->
                    com.kenkern.engine.ui.jogo.RegiaoBotao(alvo, r.left, r.top, r.right, r.bottom)
                }
            )
        }
    }
    // P1.4: overlay de toque da janela de jogo (último ponto, alvo, consumidor).
    var debugToqueJogo by remember { mutableStateOf(false) }
    // C1 (v0.1.26): EDITOR DE CÓDIGO LUA — alvo aberto (arquivo ou fonte inline).
    var alvoEditorLua by remember { mutableStateOf<com.kenkern.engine.ui.screens.AlvoEditorLua?>(null) }
    val abrirEditorLua: (com.kenkern.engine.ui.screens.AlvoEditorLua) -> Unit = { alvoEditorLua = it }
    // P1.3: watchdog — sem frame renderizado por WATCHDOG_MS em Play → "Forçar saída".
    var watchdogAlerta by remember { mutableStateOf(false) }
    LaunchedEffect(janelaDeJogo) {
        if (!janelaDeJogo) {
            if (watchdogAlerta) {
                watchdogAlerta = false
                surfaceView?.alertaWatchdog(false)
            }
            surfaceView?.definirRegioesReservadas(emptyList())
        } else {
            while (true) {
                kotlinx.coroutines.delay(500)
                val sv = surfaceView ?: continue
                val emAlerta = sv.milissegundosSemFrame() > com.kenkern.engine.ui.jogo.RoteadorToqueJogo.WATCHDOG_MS
                if (emAlerta != watchdogAlerta) {
                    watchdogAlerta = emAlerta
                    sv.alertaWatchdog(emAlerta)
                    if (emAlerta) {
                        com.kenkern.engine.diag.KenkernLog.w(
                            "EditorScreen",
                            "watchdog: sem frame por " + sv.milissegundosSemFrame() + " ms em Play — botão Forçar saída exibido"
                        )
                    }
                }
            }
        }
    }
    // U1 (v0.1.27): ABA "UI" — o TELAR ocupa a tela inteira quando ligado.
    var telaTelar by remember { mutableStateOf(false) }
    if (telaTelar) {
        TelarScreen(vm, onVoltarEditor = { telaTelar = false })
        return
    }

    // C1 (v0.1.26): quando o EDITOR DE CÓDIGO LUA está aberto, a tela inteira
    // é dele (retrato) — nada do editor de cena é composto.
    if (alvoEditorLua != null) {
        EditorLuaScreen(
            vm = vm,
            alvo = alvoEditorLua!!,
            onFechar = { alvoEditorLua = null },
            onAbrir = abrirEditorLua
        )
        return
    }

    // P1.3: BACK do sistema SEMPRE sai da janela de jogo (PLAYING ou PAUSED → STOPPED).
    BackHandler(enabled = janelaDeJogo) {
        com.kenkern.engine.diag.KenkernLog.w("EditorScreen", "BACK: saída da janela de jogo")
        vm.setPlayState(com.kenkern.engine.ui.jogo.RoteadorToqueJogo.acaoBack(vm.playState.value))
    }

    Box(Modifier.fillMaxSize().background(KenkernBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (janelaDeJogo) Modifier // jogo em TELA CHEIA (sem insets)
                    else Modifier.statusBarsPadding().navigationBarsPadding()
                )
        ) {
            if (!janelaDeJogo) {
                EditorTopBar(
                vm = vm,
                onExit = onExit,
                hierarchy = hierarchy,
                inspector = inspector,
                persistHierarchy = persistHierarchy,
                persistInspector = persistInspector,
                onResetarLayout = resetarLayout,
                mostrarAuxiliares = mostrarAuxiliares,
                onMostrarAuxiliaresChange = alternarAuxiliares,
                verPelaCamera = verPelaCamera,
                verPelaCameraDisponivel = cameraAlvoPreview != null,
                onVerPelaCameraChange = { verPelaCamera = it },
                resolutionScale = resolutionScale,
                onResolutionChange = { resolutionScale = it },
                gizmoMode = gizmoMode,
                gizmoSpace = gizmoSpace,
                snapEnabled = snapEnabled,
                onGizmoModeChange = { gizmoMode = it },
                onGizmoSpaceChange = { gizmoSpace = it },
                onSnapChange = { snapEnabled = it },
                cameraMode = cameraMode,
                onCameraModeChange = { cameraMode = it },
                onFocusClick = { surfaceView?.focusSelected() },
                debugOverlay = debugOverlay,
                onDebugToggle = { debugOverlay = !debugOverlay },
                onImportClick = onImportClick
            )
            }

            // ---------- Row central: painéis acoplados | viewport | painéis ----------
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 600.dp
                val viewportWpx = with(density) { maxWidth.toPx() }
                val viewportHpx = with(density) { maxHeight.toPx() }

                Row(Modifier.fillMaxSize()) {
                    // S6: na janela de jogo NENHUM painel do editor é composto.
                    val hShown = hierarchy.visible.value && !janelaDeJogo
                    val iShown = inspector.visible.value && !janelaDeJogo
                    val hDockedSide = wide && hShown && hierarchy.docked.value
                    val iDockedSide = wide && iShown && inspector.docked.value

                    if (hDockedSide) {
                        DockedPanel(
                            title = hierarchy.title,
                            modifier = Modifier
                                .width(hierarchy.dockWidthDp.value.dp)
                                .fillMaxHeight(),
                            onFloat = {
                                hierarchy.docked.value = false
                                persistHierarchy()
                            },
                            onHide = {
                                hierarchy.visible.value = false
                                persistHierarchy()
                            }
                        ) { HierarchyPanel(vm, onAdicionarNick = abrirAddNick) }

                        PanelResizeDivider(
                            onDrag = { dxPx ->
                                hierarchy.dockWidthDp.value = (hierarchy.dockWidthDp.value +
                                    dxPx / density.density)
                                    .coerceIn(hierarchy.minDockWidthDp, hierarchy.maxDockWidthDp)
                            },
                            onDragEnd = persistHierarchy
                        )
                    }

                    // ---------- Viewport (sempre dominante) ----------
                    // P1 (v0.1.25): onGloballyPositioned captura as coordenadas
                    // do Box (== espaço px da SurfaceView fillMaxSize) para o
                    // roteador de toque da janela de jogo.
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coordsDoViewport.value = it }
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                KenkernSurfaceView(
                                    ctx,
                                    sceneProvider = { vm.sceneRef },
                                    selectionProvider = { vm.selectionRef },
                                    onEngineReady = { controls ->
                                        vm.engineControls = controls
                                        controls.setPlayState(vm.playState.value)
                                    },
                                    onFirstTouch = {
                                        ViewportSession.gestureHintDismissed = true
                                        hintVisible = false
                                    },
                                    projectRootProvider = { vm.project.value?.root },
                                    onNickPicked = { id -> vm.select(id) },
                                    onGizmoDragEnd = { id, start, end ->
                                        vm.commitGizmoDrag(id, start, end)
                                    },
                                    onGizmoLiveUpdate = { vm.bump() }
                                ).also { surfaceView = it; it.mostrarAuxiliares = mostrarAuxiliares }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // ---------- Overlay de diagnóstico de toque (bug na TopBar) ----------
                        // SEM pointerInput: não intercepta toques — o viewport
                        // continua recebendo todos os eventos.
                        if (debugOverlay) {
                            TouchDebugOverlay(surfaceView)
                        }

                        // S6 (v0.1.24): BOTÕES DA JANELA DE JOGO — Pausar/
                        // Continuar e Sair (Stop) sobre o jogo em tela cheia.
                        // Sair restaura a cena do editor exatamente (M1.6).
                        // P1 (v0.1.25): zIndex acima do AndroidView + alvos de
                        // toque 48dp + regiões publicadas ao roteador — a cena
                        // NUNCA consome toque sobre eles (KenkernSurfaceView
                        // consulta RoteadorToqueJogo antes de consumir).
                        if (janelaDeJogo) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .zIndex(4f)
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(KenkernSurface.copy(alpha = 0.75f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                KButton(
                                    texto = if (vm.playState.value == PlayState.PAUSED) {
                                        stringResource(R.string.jogo_continuar)
                                    } else stringResource(R.string.jogo_pausar),
                                    onClick = {
                                        vm.setPlayState(
                                            if (vm.playState.value == PlayState.PLAYING) PlayState.PAUSED
                                            else PlayState.PLAYING
                                        )
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .onGloballyPositioned { coords ->
                                            val box = coordsDoViewport.value ?: return@onGloballyPositioned
                                            val off = coords.positionInWindow() - box.positionInWindow()
                                            regioesJogo[AlvoToque.BOTAO_PAUSAR] = Rect(
                                                off.x, off.y,
                                                off.x + coords.size.width, off.y + coords.size.height
                                            )
                                            publicarRegioes()
                                        }
                                )
                                KButton(
                                    texto = stringResource(R.string.jogo_sair),
                                    onClick = { vm.setPlayState(PlayState.STOPPED) },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .onGloballyPositioned { coords ->
                                            val box = coordsDoViewport.value ?: return@onGloballyPositioned
                                            val off = coords.positionInWindow() - box.positionInWindow()
                                            regioesJogo[AlvoToque.BOTAO_SAIR] = Rect(
                                                off.x, off.y,
                                                off.x + coords.size.width, off.y + coords.size.height
                                            )
                                            publicarRegioes()
                                        }
                                )
                            }

                            // P1.3: WATCHDOG — loop travado (sem frame por 3 s)
                            // → botão "Forçar saída" na FRENTE de tudo.
                            if (watchdogAlerta) {
                                Box(
                                    modifier = Modifier
                                        .zIndex(6f)
                                        .align(Alignment.TopCenter)
                                        .padding(top = 64.dp)
                                ) {
                                    KButton(
                                        texto = stringResource(R.string.jogo_forcar_saida),
                                        onClick = {
                                            com.kenkern.engine.diag.KenkernLog.e(
                                                "EditorScreen",
                                                "Forçar saída acionado pelo usuário (watchdog em alerta)"
                                            )
                                            vm.setPlayState(PlayState.STOPPED)
                                        },
                                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                    )
                                }
                            }

                            // U1 (v0.1.27): ABA "UI" — abre o TELAR (árvore 2D
                            // própria, .kui, camadas e Inspetor de UI).
                            IconButton(
                                onClick = { telaTelar = true },
                                modifier = Modifier
                                    .zIndex(5f)
                                    .align(Alignment.TopEnd)
                                    .padding(end = 8.dp, top = 96.dp)
                                    .size(40.dp)
                                    .background(KenkernSurface.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    Icons.Filled.GridOn,
                                    contentDescription = stringResource(R.string.telar_titulo),
                                    tint = KenkernTextMid
                                )
                            }

                            // P1.4: botão de debug de toque da janela de jogo.
                            IconButton(
                                onClick = { debugToqueJogo = !debugToqueJogo },
                                modifier = Modifier
                                    .zIndex(5f)
                                    .align(Alignment.TopEnd)
                                    .padding(end = 8.dp, top = 52.dp)
                                    .size(40.dp)
                                    .background(
                                        if (debugToqueJogo) KenkernAmber.copy(alpha = 0.25f) else KenkernSurface.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.CenterFocusStrong,
                                    contentDescription = stringResource(R.string.jogo_debug_toque_botao),
                                    tint = if (debugToqueJogo) KenkernAmber else KenkernTextMid
                                )
                            }
                            if (debugToqueJogo) {
                                OverlayToqueJogo(
                                    sv = surfaceView,
                                    modifier = Modifier
                                        .zIndex(5f)
                                        .align(Alignment.TopEnd)
                                        .padding(top = 96.dp, end = 10.dp)
                                )
                            }
                        }

                        // Dica de controle do viewport: some no 1º toque e não volta na sessão.
                        if (hintVisible && vm.playState.value == PlayState.STOPPED) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KenkernSurface.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (cameraMode == CameraMode.FLIGHT) stringResource(R.string.editor_hint_flight)
                                    else stringResource(R.string.editor_hint_orbit),
                                    fontSize = 12.sp,
                                    color = KenkernTextMid
                                )
                            }
                        }

                        // H3: MOLDURA DO PREVIEW "Ver pela câmera" — contorno da
                        // área da câmera (a proporção do render = do viewport) +
                        // rótulo com o nome da câmera e o botão de SAIR.
                        if (verPelaCamera && cameraAlvoPreview != null && vm.playState.value == PlayState.STOPPED) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .border(2.dp, KenkernAmber, RoundedCornerShape(4.dp))
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KenkernSurface.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Videocam,
                                    contentDescription = null,
                                    tint = KenkernAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.camera_preview_on, nickDisplayName(cameraAlvoPreview)),
                                    fontSize = 12.sp,
                                    color = KenkernTextHigh
                                )
                                TextButton(onClick = { verPelaCamera = false }) {
                                    Text(stringResource(R.string.camera_preview_exit), fontSize = 12.sp, color = KenkernAmber)
                                }
                            }
                        }

                        // Controles da câmera de VOO (joystick, subir/descer, velocidade)
                        // G3 (v0.1.19): com a janela Adicionar Nick aberta (qualquer
                        // painel modal), os controles de voo SOMEM — o joystick e os
                        // botões ficam por baixo do painel e não podem reagir a toques
                        // que eram para a janela.
                        val svForFlight = surfaceView
                        if (cameraMode == CameraMode.FLIGHT &&
                            vm.playState.value == PlayState.STOPPED &&
                            svForFlight != null && !addNickVisivel
                        ) {
                            FlightControls(svForFlight)
                        }

                        // S4 (v0.1.24): MOLDURA 2D do Joystick Nick no viewport do
                        // EDITOR — Nicks ui.* NUNCA desenham malha 3D nem cubo nem
                        // auxiliar 3D (MalhaNick.ehKindElemento2D); no editor aparecem
                        // como MOLDURA DE TELA (tamanho da classe Joystick), empilhadas
                        // no canto inferior esquerdo. Em Play o controle REAL entra
                        // (PlayControles).
                        if (vm.playState.value == PlayState.STOPPED && mostrarAuxiliares) {
                            val joysticksEditor = remember(vm.scene.value, sceneRevision) {
                                val s = vm.scene.value
                                if (s == null) emptyList()
                                else {
                                    val lista = ArrayList<com.kenkern.engine.core.Nick>(2)
                                    val pilha = ArrayDeque<com.kenkern.engine.core.Nick>()
                                    for (raiz in s.roots) pilha.addLast(raiz)
                                    while (!pilha.isEmpty()) {
                                        val n = pilha.removeLast()
                                        if (n.active && n.kind == "ui.joystick") lista.add(n)
                                        for (filho in n.children) pilha.addLast(filho)
                                    }
                                    lista
                                }
                            }
                            var deslocamento = 12.dp
                            for (j in joysticksEditor) {
                                val inst = j.findClassInstance("Joystick")
                                val tamanhoMoldura = (inst?.getFloat("tamanho", 112f) ?: 112f).coerceIn(64f, 200f)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 12.dp, bottom = deslocamento)
                                        .size(tamanhoMoldura.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(KenkernAmber.copy(alpha = 0.08f))
                                        .border(1.dp, KenkernAmber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        stringResource(R.string.joystick_moldura),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = KenkernAmber
                                    )
                                }
                                deslocamento += tamanhoMoldura.dp + 12.dp
                            }
                        }

                        // The viewport already sits inside the root safe-drawing
                        // area. Keep this control out of panel overlays; on narrow
                        // layouts the docked panels cover the viewport itself.
                        val floatingPanelCoversViewport =
                            (hShown && !hierarchy.docked.value) ||
                                (iShown && !inspector.docked.value)
                        val narrowDockCoversViewport = !wide &&
                            ((hShown && hierarchy.docked.value) ||
                                (iShown && inspector.docked.value))
                        val svForPlay = surfaceView
                        if (vm.playState.value == PlayState.PLAYING &&
                            hasActivePlayer &&
                            !addNickVisivel &&
                            !floatingPanelCoversViewport &&
                            !narrowDockCoversViewport &&
                            svForPlay != null
                        ) {
                            // M1.3: JOYSTICK NICK editável — o 1º ui.joystick ATIVO
                            // configura tamanho/opacidade/zona morta/8 direções/tipo;
                            // sem joystick na cena, usa o de teste padrão.
                            val cfg = remember(vm.scene.value, sceneRevision) {
                                com.kenkern.engine.ui.screens.JoystickUiConfig.de(vm.scene.value)
                            }
                            val estadoInput = remember { com.kenkern.engine.engine.EstadoInputJogo() }
                            LaunchedEffect(estadoInput, svForPlay) { svForPlay.estadoInput = estadoInput }
                            PlayControles(
                                sv = svForPlay,
                                estado = estadoInput,
                                cfg = cfg,
                                modifier = Modifier.fillMaxSize()
                            )
                            // M1.5: UI DE JOGO mínima — textos/contadores do Lua
                            // (ui.texto/ui.contador), lidos ~2 Hz (fpsTick).
                            var uiJogo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                            // U1.6 (v0.1.27): UI DO TELAR em Play — o .kui do projeto
                            // desenhado SOBRE o jogo (Compose) com toque sem conflito:
                            // só botão/joystick/área reclamam toque.
                            val docTelar = remember(vm.scene.value, sceneRevision) { vm.carregarUiDoc() }
                            if (docTelar != null) {
                                UiJogoTelar(
                                    doc = docTelar,
                                    valores = uiJogo,
                                    aoClicar = { nome -> svForPlay.chamarFuncaoUi(nome) },
                                    estado = estadoInput
                                )
                            }
                            LaunchedEffect(svForPlay, fpsTick) {
                                uiJogo = svForPlay.snapshotUiJogo()
                            }
                            if (uiJogo.isNotEmpty()) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                ) {
                                    for ((nome, texto) in uiJogo) {
                                        Text(
                                            "$nome: $texto",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KenkernTextHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(KenkernSurface.copy(alpha = 0.7f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // S2 (v0.1.24): OVERLAY DE DEBUG DE PLAY — botão que
                            // mostra NICKS SIMULADOS (nome + kind), velocidade
                            // vertical, "no chão" e a última colisão. Lê o
                            // snapshot do runtime a ~2 Hz (fpsTick — nunca
                            // acopla Compose ao thread GL).
                            var debugFisica by remember { mutableStateOf(false) }
                            var debugLinhas by remember { mutableStateOf<List<String>>(emptyList()) }
                            LaunchedEffect(svForPlay, fpsTick, debugFisica) {
                                debugLinhas = if (debugFisica) svForPlay.snapshotPlayDebug() else emptyList()
                            }
                            if (debugFisica) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 56.dp, end = 10.dp)
                                        .widthIn(max = 340.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KenkernSurface.copy(alpha = 0.85f))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.play_debug_titulo),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = KenkernAmber
                                    )
                                    if (debugLinhas.isEmpty()) {
                                        Text(
                                            stringResource(R.string.play_debug_vazio),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = KenkernTextMid
                                        )
                                    } else {
                                        for (linha in debugLinhas) {
                                            Text(
                                                linha,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = KenkernTextMid
                                            )
                                        }
                                    }
                                }
                            }
                            // botão SEMPRE presente em Play: alterna o overlay
                            IconButton(
                                onClick = { debugFisica = !debugFisica },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(end = 8.dp, top = 8.dp)
                                    .size(40.dp)
                                    .background(
                                        if (debugFisica) KenkernAmber.copy(alpha = 0.25f) else KenkernSurface.copy(alpha = 0.6f),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Filled.BugReport,
                                    contentDescription = stringResource(R.string.play_debug_botao),
                                    tint = if (debugFisica) KenkernAmber else KenkernTextMid
                                )
                            }
                        }

                        // Telas estreitas: painel "acoplado" vira overlay de lateral
                        if (!wide && hShown && hierarchy.docked.value) {
                            DockedPanel(
                                title = hierarchy.title,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .width(
                                        hierarchy.dockWidthDp.value
                                            .coerceAtMost((viewportWpx / density.density) * 0.6f)
                                            .dp
                                    )
                                    .fillMaxHeight(),
                                onFloat = {
                                    hierarchy.docked.value = false
                                    persistHierarchy()
                                },
                                onHide = {
                                    hierarchy.visible.value = false
                                    persistHierarchy()
                                }
                            ) { HierarchyPanel(vm, onAdicionarNick = abrirAddNick) }
                        }
                        if (!wide && iShown && inspector.docked.value) {
                            DockedPanel(
                                title = inspector.title,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(
                                        inspector.dockWidthDp.value
                                            .coerceAtMost((viewportWpx / density.density) * 0.6f)
                                            .dp
                                    )
                                    .fillMaxHeight(),
                                onFloat = {
                                    inspector.docked.value = false
                                    persistInspector()
                                },
                                onHide = {
                                    inspector.visible.value = false
                                    persistInspector()
                                }
                            ) { InspectorPanel(vm, abrirEditorLua) }
                        }

                        // Modo flutuante (exceção): contido no viewport
                        if (hShown && !hierarchy.docked.value) {
                            FloatingPanel(
                                state = hierarchy,
                                boundsWidthPx = viewportWpx,
                                boundsHeightPx = viewportHpx,
                                emFoco = painelFoco == "hierarchy",
                                onFoco = { painelFoco = "hierarchy" },
                                onPersist = persistHierarchy,
                                onDock = {
                                    hierarchy.docked.value = true
                                    persistHierarchy()
                                },
                                onHide = {
                                    hierarchy.visible.value = false
                                    persistHierarchy()
                                }
                            ) { HierarchyPanel(vm, onAdicionarNick = abrirAddNick) }
                        }
                        if (iShown && !inspector.docked.value) {
                            FloatingPanel(
                                state = inspector,
                                boundsWidthPx = viewportWpx,
                                boundsHeightPx = viewportHpx,
                                emFoco = painelFoco == "inspector",
                                onFoco = { painelFoco = "inspector" },
                                onPersist = persistInspector,
                                onDock = {
                                    inspector.docked.value = true
                                    persistInspector()
                                },
                                onHide = {
                                    inspector.visible.value = false
                                    persistInspector()
                                }
                            ) { InspectorPanel(vm, abrirEditorLua) }
                        }
                    }

                    if (iDockedSide) {
                        PanelResizeDivider(
                            onDrag = { dxPx ->
                                inspector.dockWidthDp.value = (inspector.dockWidthDp.value -
                                    dxPx / density.density)
                                    .coerceIn(inspector.minDockWidthDp, inspector.maxDockWidthDp)
                            },
                            onDragEnd = persistInspector
                        )
                        DockedPanel(
                            title = inspector.title,
                            modifier = Modifier
                                .width(inspector.dockWidthDp.value.dp)
                                .fillMaxHeight(),
                            onFloat = {
                                inspector.docked.value = false
                                persistInspector()
                            },
                            onHide = {
                                inspector.visible.value = false
                                persistInspector()
                            }
                        ) { InspectorPanel(vm, abrirEditorLua) }
                    }
                }
            }

            if (!janelaDeJogo) {
                EditorStatusBar(vm, surfaceView, fpsTick)
            }
        }

        // ---------- Janela "Adicionar Nick" (G2, v0.1.19) ----------
        // Modal na RAIZ (fora da Row central): usa a ALTURA TODA do viewport e,
        // com largura de 60% da tela, pode PASSAR POR CIMA do Inspetor acoplado —
        // o usuário decide quando abrir. TopBar (48dp) e StatusBar (24dp) ficam
        // visíveis; toques só dentro da janela (barreira de toque no painel).
        if (addNickVisivel) {
            Box(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 48.dp, bottom = 24.dp)
                        .fillMaxHeight()
                        .width(
                            (LocalConfiguration.current.screenWidthDp * 0.6f).dp
                                .coerceIn(240.dp, 640.dp)
                        )
                ) {
                    AddNickPanel(
                        vm = vm,
                        spawnXz = spawnXz,
                        onFechar = { addNickVisivel = false }
                    )
                }
            }
        }

        // Poll de FPS sem acoplar Compose ao thread GL.
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(500)
                fpsTick++
            }
        }

        // Resolução de render ajustável.
        LaunchedEffect(resolutionScale, surfaceView) {
            val sv = surfaceView ?: return@LaunchedEffect
            sv.post {
                runCatching {
                    val holder = sv.holder
                    if (resolutionScale >= 0.99f) {
                        holder.setFixedSize(0, 0)
                    } else {
                        holder.setFixedSize(
                            (sv.width * resolutionScale).toInt().coerceAtLeast(64),
                            (sv.height * resolutionScale).toInt().coerceAtLeast(64)
                        )
                    }
                }.onFailure {
                    KenkernLog.w(TAG_UI, "setFixedSize da resolução falhou — render segue em 1:1", it)
                }
            }
        }

        // Configuração do gizmo: TopBar -> SurfaceView (estado volátil lido pela GL/UI).
        LaunchedEffect(surfaceView, gizmoMode, gizmoSpace, snapEnabled) {
            surfaceView?.setGizmoConfig(gizmoMode, gizmoSpace, snapEnabled)
        }

        // Overlay de debug: liga/desliga a publicação por frame no thread GL.
        LaunchedEffect(debugOverlay, surfaceView) {
            surfaceView?.debugState?.enabled = debugOverlay
        }

        // Modo da câmera: TopBar -> câmera (Orbital <-> Voo preservando olhar/posição).
        LaunchedEffect(surfaceView, cameraMode) {
            val sv = surfaceView ?: return@LaunchedEffect
            val targetMode = if (cameraMode == CameraMode.FLIGHT) CameraMode.FLIGHT else CameraMode.ORBITAL
            if (sv.camera.mode != targetMode) sv.camera.toggleMode()
        }

        // ---------- Aviso de recuperação pós-crash ----------
        if (vm.recoveryNotice.value) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                containerColor = KenkernSurfaceOverlay,
                action = {
                    TextButton(onClick = { vm.recoveryNotice.value = false }) {
                        Text(stringResource(R.string.hub_ok), color = KenkernAmber, fontSize = 12.sp)
                    }
                }
            ) {
                Text(stringResource(R.string.editor_recovered), fontSize = 12.sp)
            }
        }

        // ---------- Diálogos da importação (opções, progresso, relatório, erro, grande) ----------
        if (opcoesImportacaoVisiveis) {
            val nomePendente = uriImportacaoPendente?.lastPathSegment?.substringAfterLast('/') ?: "modelo"
            AlertDialog(
                onDismissRequest = { opcoesImportacaoVisiveis = false; uriImportacaoPendente = null },
                containerColor = KenkernSurfaceOverlay,
                title = { Text(stringResource(R.string.import_title), fontSize = 16.sp, color = KenkernTextHigh) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (pastaImportacaoPendente) stringResource(R.string.import_folder_label, nomePendente) + "\n\n" +
                                stringResource(R.string.import_folder_note)
                            else stringResource(R.string.import_file_label, nomePendente),
                            fontSize = 12.sp, color = KenkernTextMid
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = vm.importAjustarTamanho.value,
                                onCheckedChange = { vm.importAjustarTamanho.value = it }
                            )
                            Text(stringResource(R.string.import_adjust), fontSize = 12.sp, color = KenkernTextHigh)
                        }

                        Text(stringResource(R.string.import_res_limit), fontSize = 12.sp, color = KenkernTextHigh)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (opcao in com.kenkern.engine.editor.EditorViewModel.OPCOES_LIMITE_TEXTURA) {
                                androidx.compose.material3.FilterChip(
                                    selected = vm.importLimiteTextura.intValue == opcao,
                                    onClick = { vm.importLimiteTextura.intValue = opcao },
                                    label = {
                                        Text(
                                            if (opcao == com.kenkern.engine.assets.image.PerfilTexturaImportacao.ORIGINAL)
                                                stringResource(R.string.import_res_original)
                                            else opcao.toString(),
                                            fontSize = 11.sp
                                        )
                                    },
                                    leadingIcon = null
                                )
                            }
                        }
                        if (vm.importLimiteTextura.intValue == com.kenkern.engine.assets.image.PerfilTexturaImportacao.ORIGINAL) {
                            Text(stringResource(R.string.import_res_original_note), fontSize = 11.sp, color = KenkernTextMid)
                        }

                        // B2b: QUALIDADE do encoder ETC2 — rápida por padrão
                        // (individual + diferencial + planar, paralelo); T/H só na alta.
                        Text(stringResource(R.string.import_quality_etc2), fontSize = 12.sp, color = KenkernTextHigh)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.FilterChip(
                                selected = vm.importQualidadeEtc2.value == com.kenkern.engine.assets.image.Etc2.Qualidade.RAPIDA,
                                onClick = { vm.importQualidadeEtc2.value = com.kenkern.engine.assets.image.Etc2.Qualidade.RAPIDA },
                                label = { Text(stringResource(R.string.import_fast), fontSize = 11.sp) }
                            )
                            androidx.compose.material3.FilterChip(
                                selected = vm.importQualidadeEtc2.value == com.kenkern.engine.assets.image.Etc2.Qualidade.ALTA,
                                onClick = { vm.importQualidadeEtc2.value = com.kenkern.engine.assets.image.Etc2.Qualidade.ALTA },
                                label = { Text(stringResource(R.string.import_high), fontSize = 11.sp) }
                            )
                        }

                        Text(
                            stringResource(R.string.import_scale_note),
                            fontSize = 11.sp, color = KenkernTextLow
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        opcoesImportacaoVisiveis = false
                        val uri = uriImportacaoPendente
                        uriImportacaoPendente = null
                        if (uri != null) vm.prepararImportacao(uri, pastaImportacaoPendente)
                    }) { Text(stringResource(R.string.import_btn), color = KenkernAmber, fontSize = 12.sp) }
                },
                dismissButton = {
                    TextButton(onClick = { opcoesImportacaoVisiveis = false; uriImportacaoPendente = null }) {
                        Text(stringResource(R.string.import_cancel), color = KenkernTextMid, fontSize = 12.sp)
                    }
                }
            )
        }

        DialogosImportacao(vm)

        // ---------- Status/erro ----------
        val status = vm.statusMessage.value
        if (status != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                containerColor = KenkernSurfaceOverlay,
                action = {
                    TextButton(onClick = { vm.consumeStatus() }) {
                        Text(stringResource(R.string.hub_ok), color = KenkernAmber, fontSize = 12.sp)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = KenkernError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(status, fontSize = 12.sp)
                }
            }
        }

        // ---------- Overlay de carregamento ----------
        AnimatedVisibility(
            visible = vm.isLoading.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(KenkernBackground.copy(alpha = 0.85f))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KenkernAmber, strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.editor_preparing), fontSize = 12.sp, color = KenkernTextMid)
                }
            }
        }
    }

    // Ciclo de vida: pausa/resume do GLSurfaceView + save imediato ao pausar o app.
    // Chaves estáveis: o efeito NÃO é re-executado quando surfaceView muda
    // (acesso via rememberUpdatedState) e o dispose nunca fecha o projeto —
    // apenas salva. O fechamento é responsabilidade de onExit/BackHandler.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentSurface = rememberUpdatedState(surfaceView)
    val currentVm = rememberUpdatedState(vm)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentSurface.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    currentVm.value.saveNow()
                    currentSurface.value?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { currentVm.value.saveNow() }
                .onFailure { KenkernLog.e(TAG_UI, "saveNow do dispose falhou — cena pode não ter sido salva", it) }
        }
    }
}

// ============================== TopBar (48dp) ==============================

/**
 * G1 (v0.1.19): layout em TRÊS seções —
 *   [INÍCIO FIXO] Voltar + separador
 *   [ROLÁVEL]     Undo/Redo · Play/Pause/Stop · Importar · Gizmo (Mover/Girar/
 *                 Escalar, Local/Global, Snap) · Câmera (Voo, Focar) ·
 *                 Auxiliares · Diagnóstico de toque · Resolução
 *   [FIM FIXO]    separador + Hierarquia + Inspetor + Resetar layout
 *
 * Botões com tamanho FIXO (40dp, sem weight, sem encolher). A área rolável usa
 * horizontalScroll(rememberScrollState) com FADE nas bordas quando há mais
 * conteúdo fora da vista. Os botões de painel (Hierarquia, Inspetor) e
 * "Resetar layout" ficam FORA da área rolável, fixos no fim da barra, SEMPRE
 * visíveis — o bug era a Row única com Spacer(weight): com todas as
 * ferramentas ativas a barra estourava e o Inspetor saía da tela. As larguras
 * das seções fixas vêm de TopBarLayout (testado na JVM em TopBarLayoutTest).
 */
@Composable
private fun EditorTopBar(
    vm: EditorViewModel,
    onExit: () -> Unit,
    hierarchy: PanelUiState,
    inspector: PanelUiState,
    persistHierarchy: () -> Unit,
    persistInspector: () -> Unit,
    onResetarLayout: () -> Unit,
    mostrarAuxiliares: Boolean,
    onMostrarAuxiliaresChange: (Boolean) -> Unit,
    verPelaCamera: Boolean,
    verPelaCameraDisponivel: Boolean,
    onVerPelaCameraChange: (Boolean) -> Unit,
    resolutionScale: Float,
    onResolutionChange: (Float) -> Unit,
    gizmoMode: GizmoMode,
    gizmoSpace: GizmoSpace,
    snapEnabled: Boolean,
    onGizmoModeChange: (GizmoMode) -> Unit,
    onGizmoSpaceChange: (GizmoSpace) -> Unit,
    onSnapChange: (Boolean) -> Unit,
    cameraMode: CameraMode,
    onCameraModeChange: (CameraMode) -> Unit,
    onFocusClick: () -> Unit,
    debugOverlay: Boolean,
    onDebugToggle: () -> Unit,
    onImportClick: (Boolean) -> Unit
) {
    Surface(
        color = KenkernSurface,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
        ) {
            // ---------- INÍCIO FIXO: Voltar (sempre visível) ----------
            TopBarIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.editor_back_hub)) {
                onExit()
            }
            TopBarSeparator()

            // ---------- ÁREA ROLÁVEL: ferramentas ----------
            val rolagemTopBar = rememberScrollState()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxHeight().horizontalScroll(rolagemTopBar)
                ) {
                    TopBarIcon(
                        Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_undo),
                        enabled = vm.canUndo
                    ) { vm.undo() }
                    TopBarIcon(
                        Icons.AutoMirrored.Filled.Redo, stringResource(R.string.editor_redo),
                        enabled = vm.canRedo
                    ) { vm.redo() }

                    TopBarSeparator()

                    val playing = vm.playState.value == PlayState.PLAYING
                    TopBarIcon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) stringResource(R.string.editor_pause) else stringResource(R.string.editor_play),
                        active = playing
                    ) {
                        vm.setPlayState(if (playing) PlayState.PAUSED else PlayState.PLAYING)
                    }
                    TopBarIcon(Icons.Filled.Stop, stringResource(R.string.editor_stop)) {
                        vm.setPlayState(PlayState.STOPPED)
                    }

                    TopBarSeparator()

                    // IMPORTAR MODELO (ETAPA A): menu com Arquivo e Pasta (SAF).
                    var importMenu by remember { mutableStateOf(false) }
                    Box {
                        TopBarIcon(Icons.Filled.SaveAlt, stringResource(R.string.editor_import_model)) { importMenu = true }
                        DropdownMenu(
                            expanded = importMenu,
                            onDismissRequest = { importMenu = false },
                            containerColor = KenkernSurfaceOverlay
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_import_file), fontSize = 12.sp, color = KenkernTextHigh) },
                                onClick = { importMenu = false; onImportClick(false) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.editor_import_folder), fontSize = 12.sp, color = KenkernTextHigh) },
                                onClick = { importMenu = false; onImportClick(true) }
                            )
                        }
                    }

                    TopBarSeparator()

                    // Modo de transformação: Mover / Girar / Escalar
                    TopBarIcon(
                        Icons.Filled.OpenWith, stringResource(R.string.editor_move),
                        active = gizmoMode == GizmoMode.MOVE
                    ) { onGizmoModeChange(GizmoMode.MOVE) }
                    TopBarIcon(
                        Icons.Filled.RotateRight, stringResource(R.string.editor_rotate),
                        active = gizmoMode == GizmoMode.ROTATE
                    ) { onGizmoModeChange(GizmoMode.ROTATE) }
                    TopBarIcon(
                        Icons.Filled.OpenInFull, stringResource(R.string.editor_scale),
                        active = gizmoMode == GizmoMode.SCALE
                    ) { onGizmoModeChange(GizmoMode.SCALE) }

                    // Local/Global
                    TextButton(
                        onClick = {
                            onGizmoSpaceChange(
                                if (gizmoSpace == GizmoSpace.GLOBAL) GizmoSpace.LOCAL else GizmoSpace.GLOBAL
                            )
                        },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            if (gizmoSpace == GizmoSpace.GLOBAL) stringResource(R.string.editor_global)
                            else stringResource(R.string.editor_local),
                            fontSize = 12.sp,
                            color = KenkernTextHigh
                        )
                    }

                    // Snap (0,25 m / 15° / 0,1)
                    TopBarIcon(
                        Icons.Filled.GridOn, stringResource(R.string.editor_snap),
                        active = snapEnabled
                    ) { onSnapChange(!snapEnabled) }

                    TopBarSeparator()

                    // Câmera: Orbital <-> Voo
                    TopBarIcon(
                        Icons.Filled.Flight,
                        if (cameraMode == CameraMode.FLIGHT) stringResource(R.string.editor_orbit_back)
                        else stringResource(R.string.editor_flight),
                        active = cameraMode == CameraMode.FLIGHT
                    ) {
                        onCameraModeChange(
                            if (cameraMode == CameraMode.FLIGHT) CameraMode.ORBITAL else CameraMode.FLIGHT
                        )
                    }

                    // FOCAR (F): enquadra o Nick selecionado (duplo toque no vazio = cena toda)
                    TopBarIcon(Icons.Filled.CenterFocusStrong, stringResource(R.string.editor_focus)) { onFocusClick() }

                    // H3 (v0.1.20): VER PELA CÂMERA — preview pela câmera do Nick
                    // selecionado (camera.*) ou da CÂMERA PRINCIPAL; toggle com
                    // moldura no viewport; sair volta à câmera do editor intacta.
                    TopBarIcon(
                        Icons.Filled.Videocam,
                        if (verPelaCamera) stringResource(R.string.camera_preview_exit)
                        else stringResource(R.string.camera_preview),
                        enabled = verPelaCameraDisponivel,
                        active = verPelaCamera
                    ) { onVerPelaCameraChange(!verPelaCamera) }

                    TopBarSeparator()

                    // E5: Mostrar auxiliares de editor (ícones de luz/câmera/área no
                    // viewport; padrão LIGADO; em Play eles somem de todo jeito).
                    TopBarIcon(
                        Icons.Filled.Lightbulb,
                        stringResource(R.string.editor_show_helpers),
                        active = mostrarAuxiliares
                    ) { onMostrarAuxiliaresChange(!mostrarAuxiliares) }

                    // M1.8 (v0.1.23): EXPORTAR APK DO JOGO por botão — dispara o
                    // GitHub Actions COM O PROJETO (workflow_dispatch) e o APK
                    // assinado sai como artifact. Token do usuário fica SÓ em
                    // memória (campo de senha; nunca no repo nem em log).
                    var exportarMenu by remember { mutableStateOf(false) }
                    TopBarIcon(Icons.Filled.RocketLaunch, stringResource(R.string.export_jogo)) {
                        exportarMenu = true
                    }
                    if (exportarMenu) {
                        DialogoExportarJogo(vm) { exportarMenu = false }
                    }

                    // Diagnóstico de toque: overlay ao vivo (ponto vermelho = toque,
                    // círculos verdes = alças) para ver por que seleção/gizmo não respondem.
                    TopBarIcon(
                        Icons.Filled.BugReport,
                        stringResource(R.string.editor_touch_diag),
                        active = debugOverlay
                    ) { onDebugToggle() }

                    TopBarSeparator()

                    // Resolução de render (dropdown) — DENTRO da área rolável (G1):
                    // os botões fixos do fim são só Hierarquia/Inspetor/Resetar.
                    var resolutionMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(
                            onClick = { resolutionMenu = true },
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                "${(resolutionScale * 100).toInt()}%",
                                fontSize = 12.sp,
                                color = KenkernTextMid
                            )
                        }
                        DropdownMenu(
                            expanded = resolutionMenu,
                            onDismissRequest = { resolutionMenu = false },
                            containerColor = KenkernSurfaceOverlay
                        ) {
                            for (option in listOf(1f, 0.75f, 0.5f)) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.editor_resolution, (option * 100).toInt()),
                                            fontSize = 12.sp,
                                            color = if (option == resolutionScale) KenkernAmber else KenkernTextMid
                                        )
                                    },
                                    trailingIcon = {
                                        if (option == resolutionScale) {
                                            Icon(
                                                Icons.Filled.Check, contentDescription = null,
                                                tint = KenkernAmber, modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        onResolutionChange(option)
                                        resolutionMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // G1: fade nas bordas quando há conteúdo fora da vista.
                if (rolagemTopBar.canScrollBackward) {
                    TopBarEdgeFade(inicio = true, modifier = Modifier.align(Alignment.CenterStart))
                }
                if (rolagemTopBar.canScrollForward) {
                    TopBarEdgeFade(inicio = false, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }

            // ---------- FIM FIXO: painéis + Resetar layout (SEMPRE visíveis) ----------
            // Fora da área rolável: o bug G1 espremia a Hierarquia e o Inspetor
            // nem aparecia quando as ferramentas de voo/auxiliares lotavam a barra.
            TopBarSeparator()

            TopBarIcon(
                Icons.Filled.AccountTree,
                if (hierarchy.visible.value) stringResource(R.string.editor_hide_hierarchy)
                else stringResource(R.string.editor_show_hierarchy),
                active = hierarchy.visible.value
            ) {
                hierarchy.visible.value = !hierarchy.visible.value
                persistHierarchy()
            }
            TopBarIcon(
                Icons.Filled.Tune,
                if (inspector.visible.value) stringResource(R.string.editor_hide_inspector)
                else stringResource(R.string.editor_show_inspector),
                active = inspector.visible.value
            ) {
                inspector.visible.value = !inspector.visible.value
                persistInspector()
            }

            // E4: Resetar layout — painéis de volta ao padrão profissional
            // (visíveis + acoplados + tamanho/posição padrão, nas 2 orientações).
            TopBarIcon(
                Icons.Filled.Restore,
                stringResource(R.string.editor_reset_layout)
            ) { onResetarLayout() }
        }
    }
}

/**
 * G1: faixa de FADE de 16dp na borda da área rolável da TopBar — indica que há
 * mais conteúdo fora da vista. Só é composta quando a rolagem tem conteúdo
 * escondido (canScrollBackward/canScrollForward).
 */
@Composable
private fun TopBarEdgeFade(inicio: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .width(16.dp)
            .background(
                if (inicio) {
                    Brush.horizontalGradient(listOf(KenkernSurface, Color.Transparent))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, KenkernSurface))
                }
            )
    )
}

/**
 * Botão de ícone da TopBar: 40dp de toque, descrição obrigatória.
 * Repouso: ícone cinza #CCCCCC sem fundo. Ativo: fundo #3C3C3C + ícone âmbar
 * (sem círculo colorido).
 */
@Composable
private fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = KenkernTextHigh,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    // P2 (v0.1.25): visual "Singularidade" na TopBar — superfície escura
    // translúcida + aro de 1dp em cobre + brilho ao pressionar; ativo em âmbar.
    // 40dp visual (a TopBar tem 48dp) com o alvo de toque 48dp do M3.
    val interacoes = remember { MutableInteractionSource() }
    val pressionado by interacoes.collectIsPressedAsState()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interacoes,
        modifier = Modifier.size(40.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        active -> KenkernAmber.copy(alpha = 0.25f)
                        else -> com.kenkern.engine.ui.theme.KenkernSingPreto.copy(alpha = 0.72f)
                    }
                )
                .border(
                    1.dp,
                    when {
                        !enabled -> com.kenkern.engine.ui.theme.KenkernSingCobre.copy(alpha = 0.3f)
                        active -> KenkernAmber
                        else -> com.kenkern.engine.ui.theme.KenkernSingCobre.copy(alpha = 0.8f)
                    },
                    RoundedCornerShape(8.dp)
                )
                .then(
                    if (pressionado) Modifier.background(
                        com.kenkern.engine.ui.theme.KenkernSingCobreClaro.copy(alpha = 0.18f)
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = when {
                    !enabled -> KenkernTextLow
                    active -> KenkernAmber
                    else -> tint
                }
            )
        }
    }
}

/** Separador vertical de 1dp (#3C3C3C) entre grupos da TopBar. */
@Composable
private fun TopBarSeparator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(KenkernOutline)
    )
}

// ===================== Overlay de diagnóstico de toque =====================

private val DebugRed = Color(0xFFFF3B30)
private val DebugGreen = Color(0xFF34C759)

/**
 * Overlay de diagnóstico do toque, ligado pelo botão de inseto na TopBar:
 *  - TEXTO ao vivo: posição do toque, tamanhos view/render, tempo e distância
 *    desde o DOWN, classificação (TAP/ARRASTO e por quê), raio de picking,
 *    resultado do picking (nome/"nenhum" + distância) e resultado do hit-test
 *    do gizmo (alça/"nenhuma" + distância em dp);
 *  - PONTO VERMELHO onde o toque caiu;
 *  - CÍRCULOS VERDES nas posições de tela das alças publicadas pelo thread GL.
 * Se os círculos não coincidirem com as alças desenhadas, a projeção está errada.
 *
 * SEM pointerInput em nenhum filho: o overlay NÃO intercepta toques — o
 * viewport continua recebendo todos os eventos normalmente.
 */
@Composable
private fun TouchDebugOverlay(sv: KenkernSurfaceView?) {
    // Recomposição ~10 Hz: lê os campos @Volatile do snapshot sem acoplar o
    // Compose ao thread GL.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            tick++
        }
    }
    if (sv == null) return
    val dbg = sv.debugState
    var viewPx by remember { mutableStateOf(android.util.Size(0, 0)) }

    Box(Modifier.fillMaxSize()) {
        // ---------- Canvas: ponto vermelho + círculos verdes ----------
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewPx = android.util.Size(it.width, it.height) }
        ) {
            // Alças publicadas em px de FRAMEBUFFER -> converte para px da view.
            val scale = if (dbg.renderW > 0 && viewPx.width > 0) {
                size.width / dbg.renderW.toFloat()
            } else 1f
            for (i in 0 until TransformGizmo.DEBUG_HANDLE_COUNT) {
                if (!dbg.handleValid[i]) continue
                val hx = dbg.handleX[i] * scale
                val hy = dbg.handleY[i] * scale
                if (hx < 0f || hy < 0f || hx > size.width || hy > size.height) continue
                drawCircle(DebugGreen, radius = 15f, center = Offset(hx, hy), style = Stroke(width = 3f))
            }
            // Ponto vermelho: onde o toque caiu (px da view).
            if (dbg.touchX >= 0f) {
                drawCircle(DebugRed, radius = 7f, center = Offset(dbg.touchX, dbg.touchY))
                drawCircle(DebugRed, radius = 20f, center = Offset(dbg.touchX, dbg.touchY), style = Stroke(width = 2f))
            }
        }

        // ---------- Texto ao vivo (monoespaçado, 10sp) ----------
        val lines = remember(tick) { buildDebugLines(sv, viewPx) }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            lines.forEach { line ->
                Text(
                    line,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = KenkernTextHigh
                )
            }
        }
    }
}

/** Monta as linhas de texto do overlay (roda na UI, 10 Hz — alocação ok). */
private fun buildDebugLines(sv: KenkernSurfaceView, viewPx: android.util.Size): List<String> {
    val dbg = sv.debugState
    val d = dbg.density.coerceAtLeast(0.1f)
    val lines = ArrayList<String>(9)

    lines.add(
        String.format(
            Locale.US,
            "toque: (%.0f, %.0f)px · ΔDOWN %.0fpx (%.0fdp) · %dms",
            dbg.touchX, dbg.touchY, dbg.touchMovedPx, dbg.touchMovedPx / d, dbg.touchElapsedMs
        )
    )
    lines.add("classe: ${dbg.touchClass} — ${dbg.touchReason}")

    val scale = if (dbg.renderW > 0 && viewPx.width > 0) viewPx.width.toFloat() / dbg.renderW else 1f
    lines.add(
        String.format(
            Locale.US,
            "view: %dx%dpx · render: %dx%dpx · escala %.0f%%",
            viewPx.width, viewPx.height, dbg.renderW, dbg.renderH, scale * 100f
        )
    )

    val ro = dbg.pickRayOrigin
    val rd = dbg.pickRayDir
    lines.add(
        String.format(
            Locale.US,
            "raio: o(%.2f, %.2f, %.2f) · d(%.2f, %.2f, %.2f)",
            ro[0], ro[1], ro[2], rd[0], rd[1], rd[2]
        )
    )

    val pickScreen = dbg.pickScreenDistPx / d
    lines.add(
        when {
            dbg.pickName != null ->
                String.format(
                    Locale.US,
                    "pick: “%s” · t=%.2fm · tela %.0fdp",
                    dbg.pickName, dbg.pickT, pickScreen
                )
            dbg.pickCount > 0 ->
                String.format(
                    Locale.US,
                    "pick: nenhum · AABB mais próx: %.0fdp (tol %.0fdp) · %d nicks",
                    pickScreen, dbg.pickTolPx / d, dbg.pickCount
                )
            else -> "pick: — (sem tap ainda)"
        }
    )

    lines.add(
        when {
            dbg.gizmoHit != GizmoHandle.NONE ->
                String.format(
                    Locale.US,
                    "gizmo: alça %s · dist %.0fdp (tol 28dp)",
                    dbg.gizmoHit, dbg.gizmoNearestDp
                )
            dbg.gizmoNote != null -> "gizmo: ${dbg.gizmoNote}"
            dbg.gizmoNearestDp >= 0f ->
                String.format(Locale.US, "gizmo: nenhuma · alça mais próx: %.0fdp (tol 28dp)", dbg.gizmoNearestDp)
            else -> "gizmo: — (toque com o cubo selecionado)"
        }
    )

    if (sv.isGizmoDegraded) {
        lines.add("GIZMO VAZIO (fallback do runCatching) — hit-test sem alvos")
    }

    // CÂMERA/PINÇA (diagnóstico do zoom preso): modo/distância/min/max/yaw/pitch
    // lidos da CÂMERA VIVA (mesmo padrão do FlightControls, 10 Hz); nº de dedos,
    // spans, fator do último MOVE e motivo do reset do baseline vêm do
    // TouchController (publicados por evento, só primitivos).
    val cam = sv.camera
    val isFlight = cam.mode == CameraMode.FLIGHT
    lines.add(
        String.format(
            Locale.US,
            "cam: %s · dist %.2fm [%.2f–%.0f] · dedos %d · span %.0f→%.0fpx · fator %.3f · reset: %s",
            if (isFlight) "VOO" else "ORBITAL",
            cam.distance, cam.minDistance, cam.maxDistance,
            dbg.pinchFingers, dbg.pinchStartSpan, dbg.pinchSpan,
            dbg.pinchLastFactor, dbg.pinchResetReason
        )
    )
    if (isFlight) {
        lines.add(
            String.format(
                Locale.US,
                // v0.1.12 ETAPA A: Δ = dx/dy em dp do último MOVE de olhar no voo
                "voo: yaw %.1f° · pitch %.1f° · Δ %.1f/%.1fdp · gesto: %s",
                cam.flightYawDegrees, cam.flightPitchDegrees,
                dbg.flightLookDxDp, dbg.flightLookDyDp, dbg.flightGesture
            )
        )
    }

    // CONSOLE (regra do realinhamento): erros registrados via KenkernLog aparecem
    // também aqui, no overlay — o Console da engine tem 3 destinos (logcat,
    // Diagnóstico e este overlay).
    val erros = KenkernLog.totalErros()
    if (erros > 0) {
        val ultimas = KenkernLog.registros().asReversed().take(3)
        lines.add("console: $erros erro(s) — últimos:")
        for (r in ultimas) {
            lines.add("  ${if (r.nivel == KenkernLog.Nivel.E) "E" else "W"} [${r.tag}] ${r.mensagem}" +
                (r.detalhe?.let { " — $it" } ?: ""))
        }
    }
    return lines
}

/**
 * P1.4 (v0.1.25): OVERLAY DE TOQUE da JANELA DE JOGO — mostra o último ponto
 * tocado (px da view), o alvo decidido pelo RoteadorToqueJogo e quem consumiu
 * (cena do jogo ou botão Compose). Ligado pelo botão TouchApp no canto superior
 * direito da janela de jogo. SEM pointerInput: o overlay NÃO intercepta toques.
 */
@Composable
private fun OverlayToqueJogo(sv: KenkernSurfaceView?, modifier: Modifier = Modifier) {
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            tick++
        }
    }
    if (sv == null) return
    val alvo = sv.ultimoAlvoToque
    Column(
        modifier
            .widthIn(max = 300.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            stringResource(R.string.jogo_debug_toque_titulo),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = KenkernAmber
        )
        Text(
            stringResource(
                R.string.jogo_debug_toque_ponto,
                String.format(java.util.Locale.US, "%.0f", sv.ultimoToqueX),
                String.format(java.util.Locale.US, "%.0f", sv.ultimoToqueY)
            ),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = KenkernTextHigh
        )
        Text(
            stringResource(R.string.jogo_debug_toque_alvo, alvo?.name ?: "—"),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = KenkernTextHigh
        )
        Text(
            stringResource(R.string.jogo_debug_toque_consumidor, sv.ultimoConsumidorToque),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = KenkernTextHigh
        )
    }
}

// ============================== Câmera de VOO ==============================

/**
 * Controles do modo VOO:
 *  - joystick virtual à esquerda: frente/trás/lados;
 *  - dois botões à direita: subir e descer;
 *  - slider de velocidade em escala logarítmica (0,1 a 100 m/s) no centro.
 * G3 (v0.1.19): o Box inteiro vive na ÁREA SEGURA (insets safeDrawing) — nada
 * cortado pela barra de gestos; e o composable só existe quando NÃO há painel
 * modal aberto (a condição está no EditorScreen).
 * O Box externo NÃO captura toques (só os controles): o resto da tela continua
 * entregando eventos ao viewport (arrastar gira o olhar via TouchController).
 */
@Composable
private fun FlightControls(sv: KenkernSurfaceView) {
    val cam = sv.camera
    val fwd = remember { mutableStateOf(0f) }
    val strafe = remember { mutableStateOf(0f) }
    val vert = remember { mutableStateOf(0f) }
    val knob = remember { mutableStateOf(Offset.Zero) }
    var speedT by remember { mutableStateOf(cam.flightSpeedNormalized()) }
    // T0.1: rótulo de velocidade localizado (stringResource na composição —
    // o estado guarda só o valor normalizado, o texto vem do recurso).
    val speedLabel = stringResource(R.string.editor_speed, cam.flightSpeed)

    // Movimento contínuo sincronizado com os frames (dt real, sem alocação relevante).
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos > 0L) {
                    val dt = ((now - lastNanos) / 1e9).toFloat().coerceIn(0f, 0.1f)
                    val f = fwd.value
                    val s = strafe.value
                    val v = vert.value
                    if (f != 0f || s != 0f || v != 0f) {
                        cam.moveFlight(f, s, dt)
                        cam.elevateFlight(v, dt)
                    }
                }
                lastNanos = now
            }
        }
    }

    // G3 (v0.1.19): TODOS os controles dentro da ÁREA SEGURA — o Box aplica os
    // insets (safeDrawing é consumption-aware: TopBar/StatusBar já consumidos
    // pela Column raiz entram como 0); antes o controle de velocidade ficava
    // cortado embaixo pela barra de gestos do sistema.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // ---------- Joystick virtual (canto inferior esquerdo) ----------
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .size(108.dp)
                .clip(CircleShape)
                .background(KenkernSurfaceOverlay.copy(alpha = 0.60f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            val maxR = size.width * 0.5f
                            var ox = change.position.x - cx
                            var oy = change.position.y - cy
                            val len = kotlin.math.sqrt(ox * ox + oy * oy)
                            if (len > maxR) {
                                ox = ox / len * maxR
                                oy = oy / len * maxR
                            }
                            knob.value = Offset(ox, oy)
                            strafe.value = ox / maxR
                            fwd.value = -(oy / maxR) // cima = frente
                            change.consume()
                        }
                        knob.value = Offset.Zero
                        fwd.value = 0f
                        strafe.value = 0f
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .offset { IntOffset(knob.value.x.toInt(), knob.value.y.toInt()) }
                    .clip(CircleShape)
                    .background(KenkernTextMid.copy(alpha = 0.75f))
            )
        }

        // ---------- Slider de velocidade (canto inferior central) ----------
        // G3: padding inferior garantido dentro da área segura (o controle
        // INTEIRO — rótulo + trilha + polegar — sempre visível).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .width(200.dp)
        ) {
            Text(speedLabel, fontSize = 12.sp, color = KenkernTextMid)
            Slider(
                value = speedT,
                onValueChange = { t ->
                    speedT = t
                    cam.setFlightSpeedNormalized(t)
                },
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
        }

        // ---------- Botões subir/descer (canto inferior direito) ----------
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            FlightHoldButton(Icons.Filled.KeyboardArrowUp, stringResource(R.string.editor_up)) { held ->
                vert.value = if (held) 1f else 0f
            }
            FlightHoldButton(Icons.Filled.KeyboardArrowDown, stringResource(R.string.editor_down)) { held ->
                vert.value = if (held) -1f else 0f
            }
        }
    }
}

/** Botão de voo com pressão contínua (48dp+): onHold(true) no down, onHold(false) no up. */
@Composable
private fun FlightHoldButton(
    icon: ImageVector,
    contentDescription: String,
    onHold: (Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (pressed) KenkernOutline else KenkernSurfaceOverlay.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressed = true
                    onHold(true)
                    waitForUpOrCancellation()
                    pressed = false
                    onHold(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = KenkernTextHigh,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ===================== Exportar APK do jogo (M1.8, v0.1.23) =====================

/**
 * Diálogo da EXPORTAÇÃO: repositório (dono/repo), workflow, branch e o TOKEN
 * (campo de SENHA — fica só em memória, nunca gravado nem logado). Dispara o
 * workflow_dispatch e informa o resultado; os passos e o workflow de exemplo
 * estão documentados em docs/EXPORTAR_JOGO.md.
 */
@Composable
private fun DialogoExportarJogo(vm: EditorViewModel, onFechar: () -> Unit) {
    var repositorio by remember { mutableStateOf("kokernk-ctrl/Kenkerne") }
    var workflow by remember { mutableStateOf("exportar-jogo.yml") }
    var ref by remember { mutableStateOf("main") }
    var token by remember { mutableStateOf("") }
    var mensagem by remember { mutableStateOf<String?>(null) }
    val escopo = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO) }
    AlertDialog(
        onDismissRequest = onFechar,
        containerColor = KenkernSurfaceOverlay,
        title = { Text(stringResource(R.string.export_jogo), fontSize = 16.sp, color = KenkernTextHigh) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.export_jogo_doc), fontSize = 11.sp, color = KenkernTextMid)
                OutlinedTextField(
                    value = repositorio, onValueChange = { repositorio = it },
                    label = { Text(stringResource(R.string.export_jogo_repo), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = workflow, onValueChange = { workflow = it },
                    label = { Text(stringResource(R.string.export_jogo_workflow), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = ref, onValueChange = { ref = it },
                    label = { Text(stringResource(R.string.export_jogo_ref), fontSize = 12.sp) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.export_jogo_token), fontSize = 12.sp) },
                    supportingText = { Text(stringResource(R.string.export_jogo_token_nota), fontSize = 10.sp) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                mensagem?.let {
                    Text(it, fontSize = 12.sp, color = if (it.startsWith("OK")) KenkernSuccess else KenkernError)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val projeto = vm.project.value
                if (projeto == null) {
                    mensagem = "sem projeto aberto"
                } else {
                    mensagem = "enviando…"
                    escopo.launch {
                        val erro = com.kenkern.engine.projects.ExportadorJogo.disparar(
                            com.kenkern.engine.projects.ExportadorJogo.Config(
                                repositorio = repositorio,
                                workflow = workflow,
                                ref = ref,
                                projetoNome = projeto.name,
                                token = token
                            )
                        )
                        mensagem = if (erro == null) "OK — dispatch aceito. Baixe o APK assinado em Actions → artifacts."
                        else erro
                    }
                }
            }) { Text(stringResource(R.string.export_jogo_disparar), color = KenkernAmber, fontSize = 12.sp) }
        },
        dismissButton = {
            TextButton(onClick = onFechar) { Text(stringResource(R.string.hub_cancel), color = KenkernTextMid, fontSize = 12.sp) }
        }
    )
}

// ===================== Controles de jogo (M1.3, v0.1.22) =====================

/**
 * Configuração LIDA do 1º Nick de joystick (ui.joystick) ativo na cena —
 * tipo (fixo/flutuante/dinâmico), tamanho, opacidade, zona morta, 8 direções
 * e o campo "Controla" (Player Body Nick). Leitura DEFENSIVA (Nick antigo sem
 * classe = defaults). Multitoque: joystick e botões são áreas SEPARADAS.
 */
data class JoystickUiConfig(
    val tipo: String,
    val tamanhoDp: Float,
    val opacidade: Float,
    val zonaMorta: Float,
    val oitoDirecoes: Boolean,
    val seguirNick: Boolean
) {
    companion object {
        val PADRAO = JoystickUiConfig("fixo", 112f, 0.9f, 0.15f, false, false)

        fun de(scene: com.kenkern.engine.core.Scene?): JoystickUiConfig {
            if (scene == null) return PADRAO
            val pilha = ArrayList<com.kenkern.engine.core.Nick>(16)
            for (r in scene.roots) pilha.add(r)
            var head = 0
            while (head < pilha.size) {
                val n = pilha[head++]
                if (n.active && n.kind == "ui.joystick") {
                    val inst = n.findClassInstance("Joystick")
                    if (inst != null) {
                        return JoystickUiConfig(
                            tipo = inst.getString("tipo", "fixo"),
                            tamanhoDp = inst.getFloat("tamanho", 112f).coerceIn(64f, 200f),
                            opacidade = inst.getFloat("opacidade", 0.9f).coerceIn(0.15f, 1f),
                            zonaMorta = inst.getFloat("zonaMorta", 0.15f).coerceIn(0f, 0.9f),
                            oitoDirecoes = inst.getBool("oitoDirecoes", false),
                            seguirNick = inst.getBool("seguirNick", false)
                        )
                    }
                }
                for (c in n.children) pilha.add(c)
            }
            return PADRAO
        }
    }
}

/**
 * M1.3 (v0.1.22): CONTROLES DE JOGO em Play — joystick de tela (configuração
 * do Nick) + BOTÕES DE AÇÃO (Pular/Atacar), com AÇÕES NOMEADAS no
 * [EstadoInputJogo]. MULTITOQUE REAL: joystick e cada botão são áreas de
 * pointerInput SEPARADAS (dois dedos simultâneos funcionam); a matemática
 * (normalização/zona morta/8 direções) é o [InputJogo] puro e testado.
 */
@Composable
internal fun PlayControles(
    sv: KenkernSurfaceView,
    estado: com.kenkern.engine.engine.EstadoInputJogo,
    cfg: JoystickUiConfig,
    modifier: Modifier = Modifier
) {
    val knob = remember { mutableStateOf(Offset.Zero) }
    val centroBase = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(sv) {
        onDispose {
            knob.value = Offset.Zero
            estado.resetar()
            sv.setPlayMovement(0f, 0f)
        }
    }

    Box(modifier) {
        // ---------- JOYSTICK (canto inferior esquerdo) ----------
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
        ) {
            androidx.compose.material3.Text(
                text = stringResource(R.string.play_joystick_move),
                color = KenkernTextHigh,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
            Box(
                modifier = Modifier
                    .size(cfg.tamanhoDp.dp)
                    .clip(CircleShape)
                    .graphicsLayer { alpha = cfg.opacidade }
                    .background(KenkernSurfaceOverlay.copy(alpha = 0.82f))
                    .pointerInput(cfg, estado) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun aplicar(position: Offset) {
                                val centro = if (cfg.tipo == "fixo") {
                                    centroBase.value
                                } else {
                                    // flutuante/dinâmico: o centro NASCE no toque
                                    centroBase.value = position
                                    position
                                }
                                val raioMax = size.width * 0.27f
                                val saida = FloatArray(2)
                                com.kenkern.engine.engine.InputJogo.aplicar(
                                    position.x - centro.x, position.y - centro.y,
                                    raioMax, cfg.zonaMorta, cfg.oitoDirecoes, saida
                                )
                                estado.moverX = saida[0]
                                estado.moverY = saida[1]
                                val delta = position - centro
                                val len = delta.getDistance()
                                knob.value = if (len > raioMax && len > 0f) delta * (raioMax / len) else delta
                            }
                            try {
                                centroBase.value = centerNoBox(size)
                                aplicar(down.position)
                                down.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val mudanca = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!mudanca.pressed) break
                                    aplicar(mudanca.position)
                                    mudanca.consume()
                                }
                            } finally {
                                knob.value = Offset.Zero
                                estado.moverX = 0f
                                estado.moverY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(cfg.tamanhoDp.dp - 8.dp)) {
                    val centro = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(
                        color = KenkernAmber.copy(alpha = 0.55f),
                        radius = size.minDimension * 0.47f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = KenkernAmber.copy(alpha = 0.22f),
                        radius = size.minDimension * 0.25f,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawLine(
                        color = KenkernAmber.copy(alpha = 0.24f),
                        start = Offset(centro.x - size.width * 0.34f, centro.y),
                        end = Offset(centro.x + size.width * 0.34f, centro.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = KenkernAmber.copy(alpha = 0.24f),
                        start = Offset(centro.x, centro.y - size.height * 0.34f),
                        end = Offset(centro.x, centro.y + size.height * 0.34f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            translationX = knob.value.x
                            translationY = knob.value.y
                        }
                        .clip(CircleShape)
                        .background(KenkernAmber.copy(alpha = 0.92f))
                )
            }
        }

        // ---------- BOTÕES DE AÇÃO (canto inferior direito; dedo separado) ----------
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp)
        ) {
            BotaoAcao(stringResource(R.string.play_atacar)) { pressionado ->
                estado.atacarPressionado = pressionado
            }
            BotaoAcao(stringResource(R.string.play_pular)) { pressionado ->
                estado.pularPressionado = pressionado
            }
        }
    }
}

private fun centerNoBox(size: androidx.compose.ui.unit.IntSize): Offset =
    Offset(size.width / 2f, size.height / 2f)

/** Botão de ação circular 56dp (alvo de toque ≥ 48dp) com borda de pressão. */
@Composable
private fun BotaoAcao(rotulo: String, onPressao: (Boolean) -> Unit) {
    var pressionado by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (pressionado) KenkernOutline
                else KenkernSurfaceOverlay.copy(alpha = 0.82f)
            )
            .pointerInput(rotulo) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressionado = true
                    onPressao(true)
                    waitForUpOrCancellation()
                    pressionado = false
                    onPressao(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(rotulo, fontSize = 12.sp, color = KenkernTextHigh)
    }
}

// ============================== StatusBar (24dp) ==============================

/** Estado de play, FPS, N nicks · M malhas, métricas de frame e integridade GL/.KERN. */
@Composable
private fun EditorStatusBar(vm: EditorViewModel, surfaceView: KenkernSurfaceView?, fpsTick: Int) {
    val playing = vm.playState.value == PlayState.PLAYING
    val paused = vm.playState.value == PlayState.PAUSED
    val fps = surfaceView?.rendererFps(fpsTick) ?: 0
    val updMs = surfaceView?.rendererUpdateMs(fpsTick) ?: 0f
    val drawTimeMs = surfaceView?.rendererDrawMs(fpsTick) ?: 0f
    val draws = surfaceView?.rendererDrawCalls(fpsTick) ?: 0
    // REGRA "CENA = TELA" (E6): N = total de Nicks da cena; M = GRUPOS DE
    // MATERIAL desenhados pelo renderer no último frame ("malhas").
    val sceneAtual = vm.scene.value
    val totalNicks = sceneAtual?.let { com.kenkern.engine.core.MalhaNick.contarNicks(it) } ?: 0
    val malhasDesenhadas = surfaceView?.rendererMalhasDesenhadas(fpsTick) ?: 0
    val healthy = surfaceView?.isEngineHealthy ?: true
    val gizmoDegraded = surfaceView?.isGizmoDegraded ?: false

    Surface(
        color = KenkernSurface,
        modifier = Modifier.fillMaxWidth().height(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(
                        when {
                            playing -> KenkernSuccess
                            paused -> KenkernAmber
                            else -> KenkernTextLow
                        },
                        CircleShape
                    )
            )
            Text(
                when {
                    playing -> stringResource(R.string.editor_status_play)
                    paused -> stringResource(R.string.editor_status_pause)
                    else -> stringResource(R.string.editor_status_ready)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    playing -> KenkernSuccess
                    paused -> KenkernAmber
                    else -> KenkernTextMid
                }
            )
            Text(stringResource(R.string.editor_fps, fps), fontSize = 12.sp, color = KenkernTextMid)

            // REGRA "CENA = TELA" (E6): N = Nicks da cena × M = malhas desenhadas
            // (grupos de material). Na cena padrão cada malha tem 1 grupo: M bate
            // com os Nicks visíveis com malha (checagem "cena = tela" do Diagnóstico).
            Text(
                stringResource(R.string.editor_nicks_meshes, totalNicks, malhasDesenhadas),
                fontSize = 12.sp,
                color = KenkernTextMid
            )

            // Métricas de frame: atualizadas apenas 2x/s (fpsTick), formatadas fora do thread GL.
            Text(
                text = stringResource(
                    R.string.editor_frame_metrics, updMs, drawTimeMs, draws
                ),
                fontSize = 12.sp,
                color = KenkernTextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            if (!healthy) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = KenkernError,
                    modifier = Modifier.size(12.dp)
                )
                Text(stringResource(R.string.editor_gl_limited), fontSize = 12.sp, color = KenkernError)
            }

            // Gizmo caiu no modo vazio (construção falhou): aviso visível, app vivo.
            if (gizmoDegraded) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = KenkernError,
                    modifier = Modifier.size(12.dp)
                )
                Text(stringResource(R.string.editor_gizmo_unavailable), fontSize = 12.sp, color = KenkernError)
            }

            Spacer(Modifier.weight(1f))

            Text(
                if (surfaceView?.isKernOk() == true) stringResource(R.string.editor_gl_ok)
                else stringResource(R.string.editor_gl),
                fontSize = 12.sp,
                color = KenkernTextMid
            )
        }
    }
}

// ===================== Diálogos da importação (ETAPA A) =====================

/**
 * Diálogos da importação de modelo, TODOS observando o ViewModel:
 *  - progresso por etapas com CANCELAR (a importação é cooperativa);
 *  - relatório final com "Copiar relatório" (A.4);
 *  - erro CLARO com nome + causa + o que fazer (A.6);
 *  - confirmação de arquivo grande (> 200 MB, A.6).
 */
@Composable
private fun DialogosImportacao(vm: EditorViewModel) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // ---- Progresso por etapas + cancelar ----
    val etapa = vm.importEtapa.value
    if (etapa != null) {
        AlertDialog(
            onDismissRequest = { /* modal durante a importação; sair pelo CANCELAR */ },
            containerColor = KenkernSurfaceOverlay,
            title = { Text(stringResource(R.string.import_running), fontSize = 16.sp, color = KenkernTextHigh) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = KenkernAmber, strokeWidth = 3.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(etapa, fontSize = 13.sp, color = KenkernTextHigh)
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cancelarImportacao() }) {
                    Text(stringResource(R.string.import_cancel), color = KenkernError, fontSize = 12.sp)
                }
            }
        )
    }

    // ---- Relatório (A.4) com Copiar relatório ----
    val relatorio = vm.importRelatorio.value
    if (relatorio != null) {
        AlertDialog(
            onDismissRequest = { vm.limparRelatorioImportacao() },
            containerColor = KenkernSurfaceOverlay,
            title = { Text(stringResource(R.string.import_done), fontSize = 16.sp, color = KenkernTextHigh) },
            text = {
                Column {
                    Text(
                        relatorio.textoRelatorio(),
                        fontSize = 11.sp,
                        color = if (relatorio.integridadeOk) KenkernTextMid else KenkernError,
                        modifier = Modifier
                            .height(240.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(relatorio.textoRelatorio()))
                    vm.limparRelatorioImportacao()
                }) { Text(stringResource(R.string.import_copy_report), color = KenkernAmber, fontSize = 12.sp) }
            },
            dismissButton = {
                TextButton(onClick = { vm.limparRelatorioImportacao() }) {
                    Text(stringResource(R.string.import_close), color = KenkernTextMid, fontSize = 12.sp)
                }
            }
        )
    }

    // ---- Erro CLARO (nome + causa + o que fazer) ----
    val erro = vm.importErro.value
    if (erro != null) {
        AlertDialog(
            onDismissRequest = { vm.limparErroImportacao() },
            containerColor = KenkernSurfaceOverlay,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber, contentDescription = null,
                        tint = KenkernError, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_failed_title), fontSize = 15.sp, color = KenkernTextHigh)
                }
            },
            text = {
                Text(
                    erro,
                    fontSize = 12.sp, color = KenkernTextMid,
                    modifier = Modifier
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.limparErroImportacao() }) {
                    Text(stringResource(R.string.hub_ok), color = KenkernAmber, fontSize = 12.sp)
                }
            }
        )
    }

    // ---- Arquivo grande (> 200 MB): aviso + confirmação (A.6) ----
    val pendenteGrande = vm.importPendenteGrande.value
    if (pendenteGrande != null) {
        AlertDialog(
            onDismissRequest = { vm.recusarImportacaoGrande() },
            containerColor = KenkernSurfaceOverlay,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber, contentDescription = null,
                        tint = KenkernAmber, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_big_title), fontSize = 15.sp, color = KenkernTextHigh)
                }
            },
            text = {
                Text(
                    stringResource(R.string.import_big_text, pendenteGrande.nome, pendenteGrande.tamanhoMb),
                    fontSize = 12.sp, color = KenkernTextMid
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.confirmarImportacaoGrande() }) {
                    Text(stringResource(R.string.import_big_yes), color = KenkernAmber, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.recusarImportacaoGrande() }) {
                    Text(stringResource(R.string.import_cancel), color = KenkernTextMid, fontSize = 12.sp)
                }
            }
        )
    }
}


/**
 * U1.6 (v0.1.27): a UI do TELAR desenhada SOBRE o jogo em Play (Compose) —
 * textos ligados a variáveis (ligaTexto → ui.contagem/ui.texto), botões com
 * on_click → função Lua e joystick do documento no EstadoInputJogo. NÃO
 * interativo não reclama toque (o jogo continua recebendo); só botão/joystick.
 */
@Composable
fun UiJogoTelar(
    doc: com.kenkern.engine.ui.telar.DocumentoUi,
    valores: Map<String, String>,
    aoClicar: (String) -> Unit,
    estado: com.kenkern.engine.engine.EstadoInputJogo
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val escX = w.value / com.kenkern.engine.ui.telar.LARGURA_REF
        val escY = h.value / com.kenkern.engine.ui.telar.ALTURA_REF
        for (e in doc.elementos) {
            if (e.oculto) continue
            val r = com.kenkern.engine.ui.telar.resolverRect(e, w.value.toInt(), h.value.toInt())
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = r.x * escX
                        translationY = r.y * escY
                        alpha = e.estilo.opacidade
                    }
                    .width((r.largura * escX).dp)
                    .height((r.altura * escY).dp)
            ) {
                when (e.tipo) {
                    com.kenkern.engine.ui.telar.ElementoUi.BOTAO -> {
                        Text(
                            e.texto.conteudo,
                            fontSize = ((e.texto.tamanho * escX).coerceIn(10f, 26f)).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x9914100C))
                                .border(1.dp, KenkernSingCobre)
                                .clickable {
                                    if (e.aoClicar.isNotBlank()) {
                                        KenkernLog.w("Telar", "botão de UI → função Lua '" + e.aoClicar + "'")
                                        aoClicar(e.aoClicar)
                                    }
                                }
                                .padding(4.dp)
                        )
                    }
                    com.kenkern.engine.ui.telar.ElementoUi.JOYSTICK -> {
                        var acc by remember { mutableStateOf(Offset.Zero) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0x33D98040))
                                .border(2.dp, KenkernAmber, androidx.compose.foundation.shape.CircleShape)
                                .pointerInput(e.id) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val mudanca = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!mudanca.pressed) break
                                            mudanca.consume()
                                            val centro = Offset(size.width / 2f, size.height / 2f)
                                            val raio = size.width * 0.27f
                                            val saida = FloatArray(2)
                                            com.kenkern.engine.engine.InputJogo.aplicar(
                                                mudanca.position.x - centro.x,
                                                mudanca.position.y - centro.y,
                                                raio, e.joystickZonaMorta, e.joystickOitoDirecoes, saida
                                            )
                                            estado.moverX = saida[0]
                                            estado.moverY = saida[1]
                                        }
                                    }
                                }
                        )
                    }
                    else -> {
                        val valor = if (e.ligaTexto.isNotEmpty()) (valores[e.ligaTexto] ?: "0") else e.texto.conteudo
                        Text(
                            if (e.ligaTexto.isNotEmpty()) e.texto.conteudo.substringBefore(':') + ": " + valor else valor,
                            fontSize = ((e.texto.tamanho * escX).coerceIn(10f, 30f)).sp,
                            fontWeight = if (e.texto.peso == "bold") FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}
