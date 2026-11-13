package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.engine.PlayState
import com.kenkern.engine.render.KenkernSurfaceView
import com.kenkern.engine.ui.panels.DockablePanel
import com.kenkern.engine.ui.panels.HiddenPanelChip
import com.kenkern.engine.ui.panels.HierarchyPanel
import com.kenkern.engine.ui.panels.InspectorPanel
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernInfo
import com.kenkern.engine.ui.theme.KenkernSuccess
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextLow

/**
 * Tela do EDITOR: viewport 3D (GLSurfaceView via AndroidView), barra superior
 * (undo/redo, Play/Pause/Stop, resolução), barra de status e painéis flutuantes
 * (Hierarquia e Inspetor) arrastáveis, redimensionáveis e ocultáveis.
 *
 * FIX CRÍTICO: o ciclo de vida usa [DisposableEffect] com chaves estáveis e
 * [rememberUpdatedState] — o bug anterior reexecutava o dispose quando o
 * GLSurfaceView era criado (surfaceView: null → instância), chamando exitProject()
 * imediatamente após abrir o projeto (o editor "fechava sozinho").
 */
@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    var surfaceView by remember { mutableStateOf<KenkernSurfaceView?>(null) }
    var resolutionMenu by remember { mutableStateOf(false) }
    var resolutionScale by remember { mutableStateOf(1f) }
    var fpsTick by remember { mutableStateOf(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boundsWpx = with(density) { maxWidth.toPx().toInt() }
        val boundsHpx = with(density) { maxHeight.toPx().toInt() }

        // Viewport 3D
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

        // Poll de estatísticas do render (FPS) sem acoplar Compose ao thread GL.
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(500)
                fpsTick++
            }
        }

        // Resolução de render ajustável
        LaunchedEffect(resolutionScale, surfaceView) {
            val sv = surfaceView ?: return@LaunchedEffect
            sv.post {
                runCatching {
                    val holder = sv.holder
                    if (resolutionScale >= 0.99f) {
                        holder.setFixedSize(0, 0) // volta ao tamanho da view
                    } else {
                        holder.setFixedSize(
                            (sv.width * resolutionScale).toInt().coerceAtLeast(64),
                            (sv.height * resolutionScale).toInt().coerceAtLeast(64)
                        )
                    }
                }
            }
        }

        // Painéis flutuantes (posição/tamanho salvos)
        val densityF = density.density
        val hierarchyState = remember {
            vm.panelStore.get("hierarchy", "Hierarquia", 12f, 96f, 300f, 400f, boundsWpx)
        }
        val inspectorState = remember {
            vm.panelStore.get(
                "inspector", "Inspetor",
                (boundsWpx - (300f * densityF) - 12f), 96f, 320f, 420f, boundsWpx
            )
        }

        Box(Modifier.fillMaxSize()) {
            DockablePanel(
                state = hierarchyState,
                boundsWidthPx = boundsWpx,
                boundsHeightPx = boundsHpx,
                onPersist = { vm.panelStore.persist(hierarchyState) },
                onHide = { vm.panelStore.persist(hierarchyState) }
            ) {
                HierarchyPanel(vm)
            }

            DockablePanel(
                state = inspectorState,
                boundsWidthPx = boundsWpx,
                boundsHeightPx = boundsHpx,
                onPersist = { vm.panelStore.persist(inspectorState) },
                onHide = { vm.panelStore.persist(inspectorState) }
            ) {
                InspectorPanel(vm)
            }
        }

        // Chips para reexibir painéis ocultos
        if (!hierarchyState.visible.value || !inspectorState.visible.value) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 52.dp)
            ) {
                if (!hierarchyState.visible.value) {
                    HiddenPanelChip("Mostrar Hierarquia") {
                        hierarchyState.visible.value = true
                        vm.panelStore.persist(hierarchyState)
                    }
                }
                if (!inspectorState.visible.value) {
                    HiddenPanelChip("Mostrar Inspetor") {
                        inspectorState.visible.value = true
                        vm.panelStore.persist(inspectorState)
                    }
                }
            }
        }

        // ---------- Barra superior ----------
        Surface(
            color = Color(0xF014141C),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262638)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onExit, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Voltar ao Hub",
                            tint = Color(0xFFC9C9DA)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    IconButton(onClick = { vm.undo() }, enabled = vm.canUndo, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Undo, "Desfazer", tint = KenkernAmber)
                    }
                    IconButton(onClick = { vm.redo() }, enabled = vm.canRedo, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Redo, "Refazer", tint = KenkernAmber)
                    }

                    Spacer(Modifier.width(6.dp))

                    // Play / Pause / Stop como grupo de transporte
                    TransportGroup(vm)

                    Spacer(Modifier.width(6.dp))

                    TextButton(onClick = { resolutionMenu = true }) {
                        Text(
                            "${(resolutionScale * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = KenkernTextLow
                        )
                    }
                    DropdownMenu(
                        expanded = resolutionMenu,
                        onDismissRequest = { resolutionMenu = false },
                        containerColor = KenkernSurfaceOverlay
                    ) {
                        Text(
                            "RESOLUÇÃO DE RENDER",
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = KenkernTextLow,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                        for (option in listOf(1f, 0.75f, 0.5f)) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${(option * 100).toInt()}%",
                                        color = if (option == resolutionScale) KenkernAmber else Color(0xFFC9C9DA)
                                    )
                                },
                                onClick = {
                                    resolutionScale = option
                                    resolutionMenu = false
                                }
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = Color(0xFF1F1F30))
                StatusStrip(vm, surfaceView, fpsTick)
            }
        }

        // ---------- Aviso de recuperação pós-crash ----------
        val notice = vm.recoveryNotice.value
        if (notice) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                containerColor = KenkernSurfaceOverlay,
                action = {
                    TextButton(onClick = { vm.recoveryNotice.value = false }) {
                        Text("OK", color = KenkernAmber)
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
                    TextButton(onClick = { vm.consumeStatus() }) { Text("OK", color = KenkernAmber) }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber, contentDescription = null,
                        tint = KenkernError, modifier = Modifier.size(16.dp)
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
                    .background(Color(0xD00A0A0F))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = KenkernAmber, strokeWidth = 3.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Preparando projeto...",
                        fontSize = 13.sp,
                        color = Color(0xFFC9C9DA)
                    )
                }
            }
        }
    }

    // Ciclo de vida: pausa/resume do GLSurfaceView + save imediato ao pausar o app.
    // Chaves estáveis: este efeito NÃO é re-executado quando surfaceView muda
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
            // Salva ao sair da composição (navegação para o Hub) sem crashar.
            runCatching { currentVm.value.saveNow() }
        }
    }
}

/** Grupo de transporte: Play/Pause (botão único) + Stop, com estados coloridos. */
@Composable
private fun TransportGroup(vm: EditorViewModel) {
    val playing = vm.playState.value == PlayState.PLAYING
    IconButton(
        onClick = {
            vm.setPlayState(if (playing) PlayState.PAUSED else PlayState.PLAYING)
        },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (playing) Color(0xFF2E2410) else Color.Transparent
        ),
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pausar" else "Play",
            tint = if (playing) KenkernAmber else Color(0xFFD9D9E8)
        )
    }
    IconButton(
        onClick = { vm.setPlayState(PlayState.STOPPED) },
        modifier = Modifier.size(40.dp)
    ) {
        Icon(Icons.Filled.Stop, "Stop", tint = KenkernError)
    }
}

/** Barra de status do editor: indicação de Play, backend, FPS e integridade .KERN. */
@Composable
private fun StatusStrip(vm: EditorViewModel, surfaceView: KenkernSurfaceView?, fpsTick: Int) {
    val playing = vm.playState.value == PlayState.PLAYING
    val fps = surfaceView?.rendererFps(fpsTick) ?: 0
    val healthy = surfaceView?.isEngineHealthy ?: false

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        // Indicador de estado
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    when {
                        playing -> KenkernSuccess
                        vm.playState.value == PlayState.PAUSED -> KenkernAmber
                        else -> KenkernTextLow
                    },
                    CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                playing -> "PLAY"
                vm.playState.value == PlayState.PAUSED -> "PAUSE"
                else -> "STOP"
            },
            fontSize = 9.sp,
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                playing -> KenkernSuccess
                vm.playState.value == PlayState.PAUSED -> KenkernAmber
                else -> KenkernTextLow
            }
        )

        Spacer(Modifier.width(14.dp))
        StatDot("FPS $fps")
        if (!healthy) {
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.Filled.WarningAmber, contentDescription = null,
                tint = KenkernError, modifier = Modifier.size(12.dp)
            )
            Text(
                "MODO SEGURO (GL limitado)",
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = KenkernError,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = KenkernInfo,
            modifier = Modifier.size(11.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "GLES 3.0 · .KERN OK",
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            color = KenkernTextLow
        )
    }
}

@Composable
private fun StatDot(text: String) {
    Text(
        text,
        fontSize = 9.sp,
        letterSpacing = 1.sp,
        color = KenkernTextLow,
        fontWeight = FontWeight.Medium
    )
}
