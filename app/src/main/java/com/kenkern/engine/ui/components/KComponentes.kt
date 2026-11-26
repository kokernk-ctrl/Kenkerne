package com.kenkern.engine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernSingCobre
import com.kenkern.engine.ui.theme.KenkernSingCobreClaro
import com.kenkern.engine.ui.theme.KenkernSingPreto
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow

/**
 * P2 (v0.1.25) — COMPONENTES ÚNICOS DO VISUAL "SINGULARIDADE".
 *
 * As cores vêm EXCLUSIVAMENTE dos tokens do Theme.kt (KenkernSingPreto/
 * KenkernSingCobre/KenkernSingCobreClaro/KenkernAmber) — NENHUM hex aqui.
 * Estilo do buraco negro do Hub: superfície escura translúcida + aro de 1dp
 * em cobre + brilho cobre claro ao pressionar/focar; botão primário em âmbar
 * cheio. Alvos de toque ≥ 48dp (M3 também reforça o mínimo interativo).
 * P2.3: KPanel é OPAQUE e PLANO (legibilidade e desempenho do editor) —
 * sem translucidez e SEM shader animado fora do Hub/tela inicial.
 */

private val FormaBotao = RoundedCornerShape(8.dp)

/** Brilho cobre quando pressionado (brilho = overlay do cobre claro, sem shader). */
@Composable
private fun Modifier.brilhoPressao(interacoes: MutableInteractionSource): Modifier {
    val pressionado by interacoes.collectIsPressedAsState()
    return this.then(
        if (pressionado) {
            Modifier.background(KenkernSingCobreClaro.copy(alpha = 0.18f))
        } else Modifier
    )
}

/** P2.2 — botão do visual Singularidade. [primario] = âmbar cheio com texto escuro. */
@Composable
fun KButton(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primario: Boolean = false,
    enabled: Boolean = true,
    fontSize: Int = 13
) {
    val interacoes = remember { MutableInteractionSource() }
    val fundo = if (primario) KenkernAmber else KenkernSingPreto.copy(alpha = 0.72f)
    val aro = when {
        !enabled -> KenkernSingCobre.copy(alpha = 0.35f)
        primario -> KenkernAmber
        else -> KenkernSingCobre
    }
    val conteudo = if (primario) KenkernSingPreto else KenkernTextHigh
    Box(
        modifier = modifier
            .clip(FormaBotao)
            .background(fundo)
            .border(1.dp, aro, FormaBotao)
            .brilhoPressao(interacoes)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interacoes,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = if (enabled) conteudo else KenkernTextLow,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** P2.2 — botão de ícone: 48dp, superfície escura translúcida + aro cobre. */
@Composable
fun KIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selecionado: Boolean = false,
    enabled: Boolean = true,
    tint: Color = KenkernTextHigh
) {
    val interacoes = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interacoes,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .clip(FormaBotao)
                .background(
                    when {
                        !enabled -> KenkernSingPreto.copy(alpha = 0.4f)
                        selecionado -> KenkernAmber.copy(alpha = 0.25f)
                        else -> KenkernSingPreto.copy(alpha = 0.72f)
                    }
                )
                .border(
                    1.dp,
                    if (selecionado) KenkernAmber else KenkernSingCobre.copy(alpha = 0.8f),
                    FormaBotao
                )
                .brilhoPressao(interacoes)
                .padding(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = when {
                    !enabled -> KenkernTextLow
                    selecionado -> KenkernAmber
                    else -> tint
                }
            )
        }
    }
}

/** P2.2 — chip: pill compacto com aro cobre; selecionado em âmbar. */
@Composable
fun KChip(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selecionado: Boolean = false,
    enabled: Boolean = true
) {
    val interacoes = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selecionado) KenkernAmber.copy(alpha = 0.22f) else KenkernSingPreto.copy(alpha = 0.72f))
            .border(
                1.dp,
                when {
                    !enabled -> KenkernSingCobre.copy(alpha = 0.3f)
                    selecionado -> KenkernAmber
                    else -> KenkernSingCobre
                },
                RoundedCornerShape(999.dp)
            )
            .brilhoPressao(interacoes)
            .clickable(
                interactionSource = interacoes,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = when {
                !enabled -> KenkernTextLow
                selecionado -> KenkernAmber
                else -> KenkernTextHigh
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** P2.2 — toggle (linha rótulo + Switch) com aro cobre e âmbar quando ligado. */
@Composable
fun KToggle(
    rotulo: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rotulo,
            color = if (enabled) KenkernTextHigh else KenkernTextLow,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = KenkernSingPreto,
                checkedTrackColor = KenkernAmber,
                checkedBorderColor = KenkernSingCobre,
                uncheckedThumbColor = KenkernTextHigh,
                uncheckedTrackColor = KenkernSingPreto.copy(alpha = 0.72f),
                uncheckedBorderColor = KenkernSingCobre
            )
        )
    }
}

/**
 * P2.3 — painel OPAQUE e PLANO (editor): fundo KenkernSurface sólido
 * (legibilidade e desempenho — sem translucidez, sem shader) com aro cobre
 * fino no topo como acento estático da Singularidade.
 */
@Composable
fun KPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .clip(FormaBotao)
            .background(KenkernSurface)
            .border(1.dp, KenkernSingCobre.copy(alpha = 0.45f), FormaBotao)
    ) {
        content()
    }
}
