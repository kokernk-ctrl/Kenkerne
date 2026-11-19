package com.kenkern.engine.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernAxisX
import com.kenkern.engine.ui.theme.KenkernAxisY
import com.kenkern.engine.ui.theme.KenkernAxisZ
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextMid

fun formatFloat(v: Float): String {
    val rounded = kotlin.math.round(v * 1000f) / 1000f
    return if (rounded == rounded.toLong().toFloat()) "${rounded.toLong()}" else rounded.toString()
}

/**
 * Campo numérico compacto (28dp) com rótulo de eixo colorido:
 * fundo #1E1E1E, borda 1px (#3C3C3C; âmbar no foco), texto 12sp #CCCCCC.
 * Commit no "done" do teclado ou perda de foco (não polui o undo por tecla).
 * [readOnly] = exibição (quaternion do Inspetor): altura/estilo idênticos, sem edição.
 */
@Composable
fun AxisNumberField(
    axisLabel: String,
    axisColor: Color,
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false
) {
    var text by remember(value) { mutableStateOf(formatFloat(value)) }
    var wasFocused by remember { mutableStateOf(false) }
    var focusedNow by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            axisLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = axisColor
        )
        Spacer(Modifier.width(2.dp))
        BasicTextField(
            value = text,
            onValueChange = { if (!readOnly) text = it },
            readOnly = readOnly,
            singleLine = true,
            textStyle = TextStyle(fontSize = 12.sp, color = KenkernTextHigh),
            cursorBrush = SolidColor(KenkernAmber),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .onFocusChanged { state ->
                    if (readOnly) return@onFocusChanged
                    focusedNow = state.isFocused
                    if (state.isFocused) {
                        wasFocused = true
                    } else if (wasFocused) {
                        wasFocused = false
                        val parsed = text.toFloatOrNull()
                        if (parsed != null && parsed != value) {
                            onCommit(parsed)
                        } else if (parsed == null) {
                            text = formatFloat(value)
                        }
                    }
                }
                .background(KenkernBackground, RoundedCornerShape(4.dp))
                .border(
                    1.dp,
                    if (focusedNow) KenkernAmber else KenkernOutline,
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp)
        )
    }
}

/**
 * Três campos (X, Y, Z) em linha com rótulos coloridos por eixo
 * (X #E5534B, Y #57AB5A, Z #539BF5) e commit individual.
 */
@Composable
fun Vec3AxisField(
    x: Float, y: Float, z: Float,
    onCommit: (nx: Float, ny: Float, nz: Float) -> Unit
) {
    var vx by remember(x) { mutableStateOf(x) }
    var vy by remember(y) { mutableStateOf(y) }
    var vz by remember(z) { mutableStateOf(z) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        AxisNumberField("X", KenkernAxisX, x, { vx = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
        AxisNumberField("Y", KenkernAxisY, y, { vy = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
        AxisNumberField("Z", KenkernAxisZ, z, { vz = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
    }
}

/** Campo numérico com rótulo (usado em campos de classe e quaternion). */
@Composable
fun FloatField(
    label: String,
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(formatFloat(value)) }
    var wasFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = KenkernTextHigh),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .onFocusChanged { state ->
                if (state.isFocused) {
                    wasFocused = true
                } else if (wasFocused) {
                    wasFocused = false
                    val parsed = text.toFloatOrNull()
                    if (parsed != null && parsed != value) {
                        onCommit(parsed)
                    } else if (parsed == null) {
                        text = formatFloat(value)
                    }
                }
            }
    )
}

/** Rótulo de grupo (12sp, texto secundário — sem âmbar, sem tracking). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = KenkernTextMid,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

/**
 * Seção recolhível do Inspetor: cabeçalho de 28dp com seta,
 * conteúdo exibido somente quando expandida.
 */
@Composable
fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clickable { expanded = !expanded }
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (expanded) "Recolher $title" else "Expandir $title",
                tint = KenkernTextMid,
                modifier = Modifier
                    .width(16.dp)
                    .height(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = KenkernTextMid
            )
        }
        if (expanded) content()
    }
}
