package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kenkern.engine.editor.PanelUiState
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import kotlin.math.roundToInt

/**
 * Painel flutuante do editor da KENKERN: ARRASTÁVEL (cabeçalho),
 * REDIMENSIONÁVEL (alça inferior) e OCULTÁVEL (botão X).
 * Posição e tamanho ficam salvos via PanelStateStore.
 */
@Composable
fun DockablePanel(
    state: PanelUiState,
    boundsWidthPx: Int,
    boundsHeightPx: Int,
    onPersist: () -> Unit,
    onHide: () -> Unit,
    content: @Composable () -> Unit
) {
    val visible by state.visible
    if (!visible) return

    var dragging by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val active = dragging || resizing

    fun clampPosition() {
        val minX = -(state.widthDp * density.density * 0.75f)
        val maxX = boundsWidthPx - 70f
        val minY = 0f
        val maxY = boundsHeightPx - 90f
        if (state.offsetX < minX) state.offsetX = minX
        if (state.offsetX > maxX) state.offsetX = maxX
        if (state.offsetY < minY) state.offsetY = minY
        if (state.offsetY > maxY) state.offsetY = maxY
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (active) 16.dp else 8.dp
        ),
        modifier = Modifier
            .offset { IntOffset(state.offsetX.roundToInt(), state.offsetY.roundToInt()) }
            .width(state.widthDp.dp)
            .height(state.heightDp.dp)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) {
                    KenkernAmber.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(Modifier.fillMaxSize()) {
            // Cabeçalho: arrastar + ocultar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                KenkernSurfaceOverlay.copy(alpha = 0.9f),
                                KenkernSurfaceOverlay.copy(alpha = 0.55f)
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDrag = { change, amount ->
                                change.consume()
                                state.offsetX += amount.x
                                state.offsetY += amount.y
                                clampPosition()
                            },
                            onDragEnd = { dragging = false; onPersist() },
                            onDragCancel = { dragging = false; clampPosition(); onPersist() }
                        )
                    }
            ) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = if (active) KenkernAmber else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    state.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (active) KenkernAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        state.visible.value = false
                        onHide()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Ocultar ${state.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = KenkernAmber.copy(alpha = if (active) 0.35f else 0.14f)
            )

            // Conteúdo
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content()
            }

            // Alça de redimensionamento
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(width = 44.dp, height = 16.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp))
                        .background(
                            if (resizing) KenkernAmber.copy(alpha = 0.25f) else Color.Transparent
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { resizing = true },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val dx = amount.x / density.density
                                    val dy = amount.y / density.density
                                    state.widthDp = (state.widthDp + dx).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)
                                    state.heightDp = (state.heightDp + dy).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)
                                },
                                onDragEnd = { resizing = false; onPersist() },
                                onDragCancel = { resizing = false; onPersist() }
                            )
                        }
                ) {
                    Icon(
                        Icons.Filled.DragIndicator,
                        contentDescription = "Redimensionar ${state.title}",
                        tint = if (resizing) KenkernAmber else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/** Chips que reaparecem quando um painel está oculto. */
@Composable
fun HiddenPanelChip(label: String, onRestore: () -> Unit) {
    AssistChip(
        onClick = onRestore,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            labelColor = KenkernAmber
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, KenkernAmber.copy(alpha = 0.35f)
        )
    )
}

const val MIN_WIDTH_DP: Float = 220f
const val MAX_WIDTH_DP: Float = 720f
const val MIN_HEIGHT_DP: Float = 180f
const val MAX_HEIGHT_DP: Float = 900f
