package com.kenkern.engine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

/**
 * Tela do EDITOR: viewport 3D (GLSurfaceView via AndroidView), barra superior
 * (undo/redo, Play/Pause/Stop, resolução) e painéis flutuantes
 * (Hierarquia e Inspetor) arrastáveis, redimensionáveis e ocultáveis.
 */
@Composable
fun EditorScreen(vm: EditorViewModel, onExit: () -> Unit) {
    val context = LocalContext.current
    var surfaceView by remember { mutableStateOf<KenkernSurfaceView?>(null) }
    var resolutionMenu by remember { mutableStateOf(false) }
    var resolutionScale by remember { mutableStateOf(1f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
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

        // Resolução de render ajustável
        LaunchedEffect(resolutionScale, surfaceView) {
            val sv = surfaceView ?: return@LaunchedEffect
            sv.post {
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

        // Painéis flutuantes (posição/tamanho salvos)
        val densityF = density.density
        val hierarchyState = remember {
            vm.panelStore.get(
                "hierarchy", "Hierarquia",
                12f, 90f, 300f, 400f, boundsWpx
            )
        }
        val inspectorState = remember {
            vm.panelStore.get(
                "inspector", "Inspetor",
                (boundsWpx - (300f * densityF) - 12f), 90f, 320f, 420f, boundsWpx
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
                    .padding(bottom = 18.dp)
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

        // Barra superior
        Surface(
            color = Color(0xE615151F),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, "Desfazer", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, "Refazer", tint = MaterialTheme.colorScheme.primary)
                }

                Spacer(Modifier.width(8.dp))

                when (vm.playState.value) {
                    PlayState.PLAYING -> IconButton(onClick = { vm.setPlayState(PlayState.PAUSED) }) {
                        Icon(
                            Icons.Filled.Pause, "Pausar",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    else -> IconButton(onClick = { vm.setPlayState(PlayState.PLAYING) }) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { vm.setPlayState(PlayState.STOPPED) }) {
                    Icon(Icons.Filled.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                }

                TextButton(onClick = { resolutionMenu = true }) {
                    Text("${(resolutionScale * 100).toInt()}%", fontSize = 12.sp)
                }
                DropdownMenu(expanded = resolutionMenu, onDismissRequest = { resolutionMenu = false }) {
                    for (option in listOf(1f, 0.75f, 0.5f)) {
                        DropdownMenuItem(
                            text = { Text("${(option * 100).toInt()}%") },
                            onClick = {
                                resolutionScale = option
                                resolutionMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Aviso de recuperação pós-crash
        val notice = vm.recoveryNotice.value
        if (notice) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                action = {
                    TextButton(onClick = { vm.recoveryNotice.value = false }) { Text("OK") }
                }
            ) {
                Text("Cena recuperada do auto-save", fontSize = 12.sp)
            }
        }
    }

    // Ciclo de vida: pausa/resume do GLSurfaceView + save imediato ao sair
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, surfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surfaceView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    vm.saveNow()
                    surfaceView?.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.exitProject()
        }
    }
}
