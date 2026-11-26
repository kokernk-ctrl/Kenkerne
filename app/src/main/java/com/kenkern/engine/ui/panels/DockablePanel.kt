package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kenkern.engine.R
import com.kenkern.engine.editor.PanelStateStore
import com.kenkern.engine.editor.PanelUiState
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import kotlin.math.roundToInt

/**
 * Painel ACOPLADO (docked): ocupa a lateral do editor, dentro da estrutura
 * fixa (TopBar / Row central / StatusBar) — nada sobrepõe o viewport.
 * O menu do cabeçalho oferece as exceções: flutuar e ocultar.
 * E4 (v0.1.18): BARREIRA DE TOQUE — em telas estreitas o painel "acoplado"
 * vira OVERLAY dentro do viewport; sem pointerInput os toques na área vazia
 * atravessavam para a câmera.
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

    Surface(modifier = modifier.pointerInput(Unit) { detectTapGestures { } }, color = KenkernSurface) {
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
                            contentDescription = stringResource(R.string.panel_options, title),
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
                            text = { Text(stringResource(R.string.panel_float), fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onFloat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.panel_hide), fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
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
 * Painel FLUTUANTE (exceção) — E4 (v0.1.18) corrigido de ponta a ponta:
 *  - MOVER pelo cabeçalho e REDIMENSIONAR pela alça do CANTO INFERIOR DIREITO
 *    (alvo de toque 48dp; mínimo [PanelStateStore.MIN_FLOAT_W/H_DP], máximo =
 *    viewport);
 *  - SEMPRE dentro do viewport (clamp [PanelStateStore.clampDentro] a cada
 *    arrasto e no fim);
 *  - TRAZ PARA FRENTE ao tocar ([emFoco] → [zIndex]; o Editor guarda quem tem
 *    o foco);
 *  - botão ACOPLAR no cabeçalho (ícone alfinete) + itens de menu;
 *  - BARREIRA DE TOQUE: o card inteiro é hit-testável — toque no painel NUNCA
 *    chega à câmera do viewport;
 *  - posição/tamanho em [PanelUiState] OBSERVÁVEL (era var comum: o painel
 *    nunca movia) e SALVOS por painel e por ORIENTAÇÃO (PanelStateStore).
 */
@Composable
fun FloatingPanel(
    state: PanelUiState,
    boundsWidthPx: Float,
    boundsHeightPx: Float,
    emFoco: Boolean,
    onFoco: () -> Unit,
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

    val wPx = state.floatW.value * density.density
    val hPx = state.floatH.value * density.density

    fun clampPosicao() {
        val (cx, cy) = PanelStateStore.clampDentro(
            state.floatX.value, state.floatY.value, wPx, hPx, boundsWidthPx, boundsHeightPx
        )
        state.floatX.value = cx
        state.floatY.value = cy
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = KenkernSurface), // P2.3: OPAQUE
        elevation = CardDefaults.cardElevation(defaultElevation = if (active) 12.dp else 6.dp),
        modifier = Modifier
            .zIndex(if (emFoco) 2f else 1f)
            .offset { IntOffset(state.floatX.value.roundToInt(), state.floatY.value.roundToInt()) }
            .width(state.floatW.value.dp)
            .height(state.floatH.value.dp)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) KenkernAmber.copy(alpha = 0.7f) else com.kenkern.engine.ui.theme.KenkernSingCobre.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
            // E4: barreira de toque do card — hit-testável em TODA a área: o
            // toque no painel nunca atravessa para o viewport/câmera.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize()) {
            // Cabeçalho arrastável: ACOPLAR (alfinete) + título + menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(com.kenkern.engine.ui.theme.KenkernSurfaceHigh)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                dragging = true
                                onFoco()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                state.floatX.value += amount.x
                                state.floatY.value += amount.y
                                clampPosicao()
                            },
                            onDragEnd = { dragging = false; clampPosicao(); onPersist() },
                            onDragCancel = { dragging = false; clampPosicao(); onPersist() }
                        )
                    }
                    .padding(start = 4.dp)
            ) {
                // E4: botão ACOPLAR visível direto no cabeçalho (alvo 48dp).
                IconButton(onClick = onDock, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.panel_dock, state.title),
                        tint = KenkernTextLow,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Filled.DragIndicator,
                    contentDescription = null,
                    tint = if (active) KenkernAmber else KenkernTextLow,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    state.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = KenkernTextMid,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true; onFoco() }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.panel_options, state.title),
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
                            text = { Text(stringResource(R.string.panel_dock), fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
                            onClick = {
                                menuOpen = false
                                onDock()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.panel_hide), fontSize = MaterialTheme.typography.bodyMedium.fontSize) },
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

            // Alça de redimensionamento no CANTO INFERIOR DIREITO — alvo de
            // toque 48dp (E4; era 44×14dp, abaixo do mínimo) com a pega visível
            // de 14dp dentro.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(48.dp)
                        .background(if (resizing) KenkernAmber.copy(alpha = 0.25f) else Color.Transparent)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    resizing = true
                                    onFoco()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val maxWdp = boundsWidthPx / density.density
                                    val maxHdp = boundsHeightPx / density.density
                                    state.floatW.value = (state.floatW.value + amount.x / density.density)
                                        .coerceIn(PanelStateStore.MIN_FLOAT_W_DP, maxWdp)
                                    state.floatH.value = (state.floatH.value + amount.y / density.density)
                                        .coerceIn(PanelStateStore.MIN_FLOAT_H_DP, maxHdp)
                                    clampPosicao()
                                },
                                onDragEnd = { resizing = false; clampPosicao(); onPersist() },
                                onDragCancel = { resizing = false; clampPosicao(); onPersist() }
                            )
                        }
                ) {
                    Icon(
                        Icons.Filled.DragIndicator,
                        contentDescription = stringResource(R.string.panel_resize, state.title),
                        tint = if (resizing) KenkernAmber else KenkernTextLow,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
