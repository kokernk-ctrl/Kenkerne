package com.kenkern.engine.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.core.input.GameplayInput
import com.kenkern.engine.core.input.StickType
import com.kenkern.engine.render.KenkernSurfaceView
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextHigh

/** One Play-only pointer router handles each contact independently. */
@Composable
internal fun PlayJoystick(sv: KenkernSurfaceView, modifier: Modifier = Modifier) {
    val session by sv.playInput.collectAsState()
    val input = session ?: return
    if (input.controls.isEmpty()) return
    val redraw = remember(input) { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val fallbackText = stringResource(R.string.play_joystick_move)
    val labels = remember(input, measurer, fallbackText) {
        input.controls.map { c ->
            if (c.config.button || c.fallback) measurer.measure(
                if (c.fallback) fallbackText else c.config.action,
                style = TextStyle(fontSize = 11.sp, color = KenkernTextHigh), maxLines = 1
            ) else null
        }
    }
    if (input.hasProjection) LaunchedEffect(input) {
        val last = LongArray(input.controls.size) { Long.MIN_VALUE }
        while (true) withFrameNanos {
            for (i in last.indices) {
                val projection = input.controls[i].projection
                if (last[i] != projection) {
                    last[i] = projection
                    redraw.intValue++
                }
            }
        }
    }
    Canvas(modifier.pointerInput(input) {
        try {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    for (i in event.changes.indices) {
                        val change = event.changes[i]
                        val id = change.id.value
                        val position = change.position
                        val owned = when {
                            change.isConsumed -> input.up(id)
                            !change.pressed -> input.up(id)
                            !change.previousPressed -> input.down(id, position.x, position.y, size.width.toFloat(), size.height.toFloat(), density)
                            else -> input.move(id, position.x, position.y, size.width.toFloat(), size.height.toFloat(), density)
                        }
                        if (owned) {
                            change.consume()
                            redraw.intValue++
                        }
                    }
                }
            }
        } finally {
            input.cancel()
        }
    }) {
        redraw.intValue // invalidate draw only, never recompose for touch/projection
        synchronized(input) {
            with(input) {
                // Earlier entries win touch ties; draw those on top.
                for (i in controls.indices.reversed()) {
                    val control = controls[i]
                    val cfg = control.config
                    val held = control.sample.pressed
                    if (!held && cfg.type != StickType.FIXED) continue
                    val anchor = control.anchor(size.width, size.height, density)
                    if (anchor == Long.MIN_VALUE) continue
                    val center = if (held && cfg.type != StickType.FIXED) Offset(control.baseX, control.baseY)
                    else Offset(GameplayInput.unpackX(anchor), GameplayInput.unpackY(anchor))
                    val r = control.radius(size.width, size.height, density)
                    val alpha = cfg.opacity
                    drawCircle(KenkernSurfaceOverlay.copy(alpha = alpha), r, center)
                    drawCircle(KenkernAmber.copy(alpha = .55f * alpha), r * .91f, center, style = Stroke(2.dp.toPx()))
                    if (!cfg.button) {
                        val sample = control.sample
                        drawCircle(KenkernAmber.copy(alpha = alpha), r * .34f,
                            center + Offset(sample.x * r * .54f, sample.y * r * .54f))
                    } else if (held) {
                        drawCircle(KenkernAmber.copy(alpha = .45f * alpha), r * .87f, center)
                    }
                    val label = labels[i]
                    if (label != null) drawText(label, topLeft = Offset(center.x - label.size.width / 2f,
                        if (control.fallback) center.y - r - label.size.height else center.y - label.size.height / 2f), alpha = alpha)
                }
            }
        }
    }
}
