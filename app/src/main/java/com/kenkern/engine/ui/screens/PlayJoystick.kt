package com.kenkern.engine.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kenkern.engine.R
import com.kenkern.engine.render.KenkernSurfaceView
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextHigh

/** Minimal touch movement control for the active player during gameplay. */
@Composable
internal fun PlayJoystick(
    sv: KenkernSurfaceView,
    modifier: Modifier = Modifier
) {
    val knob = remember { mutableStateOf(Offset.Zero) }

    // A state transition or removal of this overlay must never leave movement held.
    DisposableEffect(sv) {
        onDispose {
            knob.value = Offset.Zero
            sv.setPlayMovement(0f, 0f)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        androidx.compose.material3.Text(
            text = stringResource(R.string.play_joystick_move),
            color = KenkernTextHigh,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(KenkernSurfaceOverlay.copy(alpha = 0.82f))
                .pointerInput(sv) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun updateMovement(position: Offset) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxRadius = size.width * 0.27f
                            var delta = position - center
                            val length = delta.getDistance()
                            if (length > maxRadius && length > 0f) {
                                delta *= maxRadius / length
                            }
                            knob.value = delta
                            sv.setPlayMovement(
                                (delta.x / maxRadius).coerceIn(-1f, 1f),
                                (delta.y / maxRadius).coerceIn(-1f, 1f)
                            )
                        }
                        try {
                            updateMovement(down.position)
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                updateMovement(change.position)
                                change.consume()
                            }
                        } finally {
                            knob.value = Offset.Zero
                            sv.setPlayMovement(0f, 0f)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(104.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = KenkernAmber.copy(alpha = 0.55f),
                    radius = size.minDimension * 0.47f,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = KenkernAmber.copy(alpha = 0.22f),
                    radius = size.minDimension * 0.25f,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawLine(
                    color = KenkernAmber.copy(alpha = 0.24f),
                    start = Offset(center.x - size.width * 0.34f, center.y),
                    end = Offset(center.x + size.width * 0.34f, center.y),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = KenkernAmber.copy(alpha = 0.24f),
                    start = Offset(center.x, center.y - size.height * 0.34f),
                    end = Offset(center.x, center.y + size.height * 0.34f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        translationX = knob.value.x
                        translationY = knob.value.y
                    }
                    .clip(CircleShape)
                    .background(KenkernAmber.copy(alpha = 0.92f))
            )
        }
    }
}
