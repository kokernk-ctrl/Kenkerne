package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.editor.PanelUiState
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.render.KenkernSurfaceView
import java.util.Locale
import com.kenkern.engine.ui.panels.DockedPanel
import com.kenkern.engine.ui.panels.FloatingPanel
import com.kenkern.engine.ui.panels.HierarchyPanel
import com.kenkern.engine.ui.panels.InspectorPanel
import com.kenkern.engine.ui.panels.PanelResizeDivider
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSuccess
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

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
    var surfaceView by remember { mutableStateOf<KenkernSurfaceView?>(null) }
    var resolutionScale by remember { mutableStateOf(1f) }
    var fpsTick by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    val hierarchy = remember {
        vm.panelStore.get("hierarchy", "Hierarquia", 200f, 140f, 320f, 280f, 340f)
    }
    val inspector = remember {
        vm.panelStore.get("inspector", "Inspetor", 240f, 180f, 360f, 300f, 400f)
    }
    val persistHierarchy = remember { { vm.panelStore.persist(hierarchy) } }
    val persistInspector = remember { { vm.panelStore.persist(inspector) } }

    Box(Modifier.fillMaxSize().background(KenkernBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            EditorTopBar(
                vm = vm,
                onExit = onExit,
                hierarchy = hierarchy,
                inspector = inspector,
                persistHierarchy = persistHierarchy,
                persistInspector = persistInspector,
                resolutionScale = resolutionScale,
                onResolutionChange = { resolutionScale = it }
            )

            // ---------- Row central: painéis acoplados | viewport | painéis ----------
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 600.dp
                val viewportWpx = with(density) { maxWidth.toPx() }
                val viewportHpx = with(density) { maxHeight.toPx() }

                Row(Modifier.fillMaxSize()) {
                    val hShown = hierarchy.visible.value
                    val iShown = inspector.visible.value
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
                        ) { HierarchyPanel(vm) }

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
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        AndroidView(
                            factory = { ctx ->
                                KenkernSurfaceView(
                                    ctx,
                                    sceneProvider = { vm.sceneRef },
                                    selectionProvider = { vm.selectionRef },
                                    onEngineReady = { controls ->
                                        vm.engineControls = controls
                                        controls.setPlayState(vm.playState.value)
                                    }
                                ).also { surfaceView = it }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Dica de controle do viewport (feedback de uso, discreta)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(KenkernSurface.copy(alpha = 0.75f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "1 dedo: orbitar · 2 dedos: mover e zoom",
                                fontSize = 12.sp,
                                color = KenkernTextMid
                            )
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
                            ) { HierarchyPanel(vm) }
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
                            ) { InspectorPanel(vm) }
                        }

                        // Modo flutuante (exceção): contido no viewport
                        if (hShown && !hierarchy.docked.value) {
                            FloatingPanel(
                                state = hierarchy,
                                boundsWidthPx = viewportWpx,
                                boundsHeightPx = viewportHpx,
                                onPersist = persistHierarchy,
                                onDock = {
                                    hierarchy.docked.value = true
                                    persistHierarchy()
                                },
                                onHide = {
                                    hierarchy.visible.value = false
                                    persistHierarchy()
                                }
                            ) { HierarchyPanel(vm) }
                        }
                        if (iShown && !inspector.docked.value) {
                            FloatingPanel(
                                state = inspector,
                                boundsWidthPx = viewportWpx,
                                boundsHeightPx = viewportHpx,
                                onPersist = persistInspector,
                                onDock = {
                                    inspector.docked.value = true
                                    persistInspector()
                                },
                                onHide = {
                                    inspector.visible.value = false
                                    persistInspector()
                                }
                            ) { InspectorPanel(vm) }
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
                        ) { InspectorPanel(vm) }
                    }
                }
            }

            EditorStatusBar(vm, surfaceView, fpsTick)
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
                }
            }
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
                        Text("OK", color = KenkernAmber, fontSize = 12.sp)
                    }
                }
            ) {
                Text("Cena recuperada do auto-save", fontSize = 12.sp)
            }
        }

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
                        Text("OK", color = KenkernAmber, fontSize = 12.sp)
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
                    Text("Preparando projeto...", fontSize = 12.sp, color = KenkernTextMid)
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
        }
    }
}

// ============================== TopBar (48dp) ==============================

/** Voltar | Undo | Redo | sep | Play/Pause/Stop | espaço | resolução | painéis. */
@Composable
private fun EditorTopBar(
    vm: EditorViewModel,
    onExit: () -> Unit,
    hierarchy: PanelUiState,
    inspector: PanelUiState,
    persistHierarchy: () -> Unit,
    persistInspector: () -> Unit,
    resolutionScale: Float,
    onResolutionChange: (Float) -> Unit
) {
    Surface(
        color = KenkernSurface,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
        ) {
            TopBarIcon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar ao Hub", tint = KenkernTextMid) {
                onExit()
            }
            TopBarIcon(
                Icons.AutoMirrored.Filled.Undo, "Desfazer",
                tint = KenkernAmber, enabled = vm.canUndo
            ) { vm.undo() }
            TopBarIcon(
                Icons.AutoMirrored.Filled.Redo, "Refazer",
                tint = KenkernAmber, enabled = vm.canRedo
            ) { vm.redo() }

            TopBarSeparator()

            val playing = vm.playState.value == PlayState.PLAYING
            TopBarIcon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) "Pausar" else "Reproduzir",
                tint = if (playing) KenkernAmber else KenkernTextHigh,
                active = playing
            ) {
                vm.setPlayState(if (playing) PlayState.PAUSED else PlayState.PLAYING)
            }
            TopBarIcon(Icons.Filled.Stop, "Parar", tint = KenkernError) {
                vm.setPlayState(PlayState.STOPPED)
            }

            Spacer(Modifier.weight(1f))

            // Resolução de render (dropdown)
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
                                    "Resolução ${(option * 100).toInt()}%",
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

            TopBarSeparator()

            TopBarIcon(
                Icons.Filled.AccountTree,
                if (hierarchy.visible.value) "Ocultar Hierarquia" else "Mostrar Hierarquia",
                tint = if (hierarchy.visible.value) KenkernAmber else KenkernTextMid,
                active = hierarchy.visible.value
            ) {
                hierarchy.visible.value = !hierarchy.visible.value
                persistHierarchy()
            }
            TopBarIcon(
                Icons.Filled.Tune,
                if (inspector.visible.value) "Ocultar Inspetor" else "Mostrar Inspetor",
                tint = if (inspector.visible.value) KenkernAmber else KenkernTextMid,
                active = inspector.visible.value
            ) {
                inspector.visible.value = !inspector.visible.value
                persistInspector()
            }
        }
    }
}

/** Botão de ícone da TopBar: 40dp de toque, descrição obrigatória, estado ativo âmbar. */
@Composable
private fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = KenkernTextMid,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (active) KenkernAmber.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = tint,
            disabledContentColor = KenkernTextLow
        )
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

/** Separador vertical de 1dp dentro da grade de 4dp. */
@Composable
private fun TopBarSeparator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(KenkernOutlineSoft)
    )
}

// ============================== StatusBar (24dp) ==============================

/** Estado de play, FPS, métricas de frame (update/draw/draw calls) e integridade GL/.KERN. */
@Composable
private fun EditorStatusBar(vm: EditorViewModel, surfaceView: KenkernSurfaceView?, fpsTick: Int) {
    val playing = vm.playState.value == PlayState.PLAYING
    val paused = vm.playState.value == PlayState.PAUSED
    val fps = surfaceView?.rendererFps(fpsTick) ?: 0
    val updMs = surfaceView?.rendererUpdateMs(fpsTick) ?: 0f
    val drawTimeMs = surfaceView?.rendererDrawMs(fpsTick) ?: 0f
    val draws = surfaceView?.rendererDrawCalls(fpsTick) ?: 0
    val healthy = surfaceView?.isEngineHealthy ?: true

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
                    playing -> "PLAY"
                    paused -> "PAUSA"
                    else -> "PRONTO"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                color = when {
                    playing -> KenkernSuccess
                    paused -> KenkernAmber
                    else -> KenkernTextMid
                }
            )
            Text("$fps FPS", fontSize = 12.sp, color = KenkernTextMid)

            // Métricas de frame: atualizadas apenas 2x/s (fpsTick), formatadas fora do thread GL.
            Text(
                text = String.format(
                    Locale.US, "%.1f ms upd · %.1f ms draw · %d dc", updMs, drawTimeMs, draws
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
                Text("GL limitado (modo seguro)", fontSize = 12.sp, color = KenkernError)
            }

            Spacer(Modifier.weight(1f))

            Text(
                if (surfaceView?.isKernOk() == true) "OpenGL ES · .KERN OK" else "OpenGL ES",
                fontSize = 12.sp,
                color = KenkernTextMid
            )
        }
    }
}
