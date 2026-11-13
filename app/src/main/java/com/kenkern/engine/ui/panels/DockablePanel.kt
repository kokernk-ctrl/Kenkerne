package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kenkern.engine.editor.PanelUiState
import com.kenkern.engine.editor.PanelStateStore
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Painel ACOPLADO (docked): ocupa a lateral do editor, dentro da estrutura
 * fixa (TopBar / Row central / StatusBar) — nada sobrepõe o viewport.
 * O menu do cabeçalho oferece as exceções: flutuar e ocultar.
 */
@Composable
fun DockedPanel(
    title: String,
    modifier: Modifier = Modifier,
    onFloat: () -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(modifier = modifier, color = KenkernSurface) {
        Column(Modifier.fillMaxSize()) {
            // Cabeçalho 40dp: título + menu (Flutuar / Ocultar)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(start = 12.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = KenkernTextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Opções do painel $title",
                            tint = KenkernTextMid,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text("Flutuar sobre o viewport", fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onFloat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ocultar painel", fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onHide()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }
        }
    }
}

/**
 * Divisor arrastável de 6dp entre painel acoplado e viewport.
 * Área de toque é a faixa inteira de 6dp; a linha de 1dp indica o limite.
 * [onDrag] recebe o delta X em PIXELS; [onDragEnd] persiste o estado.
 */
@Composable
fun PanelResizeDivider(
    onDrag: (deltaXpx: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(6.dp)
            .fillMaxHeight()
            .background(if (dragging) KenkernAmber.copy(alpha = 0.25f) else Color.Transparent)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount.x)
                    },
                    onDragEnd = {
                        dragging = false
                        onDragEnd()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragEnd()
                    }
                )
            }
    ) {
        Box(
            Modifier
                .width(1.dp)
                .height(36.dp)
                .background(if (dragging) KenkernAmber else KenkernOutline)
        )
    }
}

/**
 * Painel FLUTUANTE (exceção): card arrastável pelo cabeçalho e redimensionável
 * pela alça inferior, contido na área do viewport (nunca cobre TopBar/StatusBar).
 */
@Composable
fun FloatingPanel(
    state: PanelUiState,
    boundsWidthPx: Float,
    boundsHeightPx: Float,
    onPersist: () -> Unit,
    onDock: () -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val active = dragging || resizing

    fun clampPosition() {
        val minX = -(state.floatW * density.density * 0.6f)
        val maxX = boundsWidthPx - 64f
        val minY = 0f
        val maxY = max(0f, boundsHeightPx - 72f)
        state.floatX = state.floatX.coerceAtLeast(minX).coerceAtMost(maxOf(minX, maxX))
        state.floatY = state.floatY.coerceIn(minY, maxY)
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KenkernSurface.copy(alpha = 0.98f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 12.dp else 6.dp),
        modifier = Modifier
            .offset { IntOffset(state.floatX.roundToInt(), state.floatY.roundToInt()) }
            .width(state.floatW.dp)
            .height(state.floatH.dp)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) KenkernAmber.copy(alpha = 0.7f) else KenkernOutlineSoft,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Cabeçalho arrastável + menu (Acoplar / Ocultar)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDrag = { change, amount ->
                                change.consume()
                                state.floatX += amount.x
                                state.floatY += amount.y
                                clampPosition()
                            },
                            onDragEnd = { dragging = false; onPersist() },
                            onDragCancel = { dragging = false; clampPosition(); onPersist() }
                        )
                    }
                    .padding(start = 10.dp)
            ) {
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = if (active) KenkernAmber else KenkernTextLow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = KenkernTextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Opções do painel ${state.title}",
                            tint = KenkernTextMid,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        DropdownMenuItem(
                            text = { Text("Acoplar à lateral", fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onDock()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Ocultar painel", fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onHide()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }

            // Alça de redimensionamento
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 44.dp, height = 14.dp)
                        .background(if (resizing) KenkernAmber.copy(alpha = 0.25f) else Color.Transparent)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { resizing = true },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val dDp = amount.x / density.density
                                    val dHDp = amount.y / density.density
                                    state.floatW = (state.floatW + dDp)
                                        .coerceIn(PanelStateStore.MIN_FLOAT_W_DP, 560f)
                                    state.floatH = (state.floatH + dHDp)
                                        .coerceIn(PanelStateStore.MIN_FLOAT_H_DP, 800f)
                                },
                                onDragEnd = { resizing = false; onPersist() },
                                onDragCancel = { resizing = false; onPersist() }
                            )
                        }
                ) {
                    Icon(
                        Icons.Filled.DragIndicator,
                        contentDescription = "Redimensionar ${state.title}",
                        tint = if (resizing) KenkernAmber else KenkernTextLow,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
