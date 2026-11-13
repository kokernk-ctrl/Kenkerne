package com.kenkern.engine.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun formatFloat(v: Float): String {
    val rounded = kotlin.math.round(v * 1000f) / 1000f
    return if (rounded == rounded.toLong().toFloat()) "${rounded.toLong()}" else rounded.toString()
}

/**
 * Campo numérico float com commit no "done" do teclado ou perda de foco
 * (evita poluir o histórico de undo a cada tecla).
 */
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
        label = { Text(label, fontSize = 10.sp) },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
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

/** Três campos (X, Y, Z) em linha com commit individual. */
@Composable
fun Vec3Field(
    labelX: String,
    labelY: String,
    labelZ: String,
    x: Float, y: Float, z: Float,
    onCommit: (nx: Float, ny: Float, nz: Float) -> Unit
) {
    var vx by remember(x) { mutableStateOf(x) }
    var vy by remember(y) { mutableStateOf(y) }
    var vz by remember(z) { mutableStateOf(z) }
    Row(Modifier.fillMaxWidth()) {
        FloatField(labelX, x, { vx = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        FloatField(labelY, y, { vy = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        FloatField(labelZ, z, { vz = it; onCommit(vx, vy, vz) }, Modifier.weight(1f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 4.dp)
    )
}
