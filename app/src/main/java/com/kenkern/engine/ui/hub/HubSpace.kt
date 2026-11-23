package com.kenkern.engine.ui.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kenkern.engine.diag.KenkernLog
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** Decorative only: two depth layers drift behind a lensed, tilted accretion disk. */
@Composable
internal fun HubSpace(visible: Boolean, modifier: Modifier) {
    val context = LocalContext.current.applicationContext
    val enabled = rememberHubMotionSetting(context)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled, visible, lifecycle) {
        if (!enabled || !visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val start = SystemClock.uptimeMillis()
            val base = phase.floatValue
            while (true) {
                delay(HUB_FRAME_INTERVAL_MS)
                phase.floatValue = (base + (SystemClock.uptimeMillis() - start) * TAU / 22_000f) % TAU
            }
        }
    }
    Box(modifier.drawWithCache {
        val h = size.height
        val w = size.width
        val center = Offset(w * 0.76f, h * 0.52f)
        val radius = minOf(h * 0.225f, w * 0.12f).coerceAtLeast(1f)
        val stars = Random(432018).let { random ->
            List(72) {
                val near = it % 4 == 0
                Star(Offset(random.nextFloat() * (w + 18.dp.toPx()) - 9.dp.toPx(), random.nextFloat() * h),
                    (if (near) 1.02f else 0.53f).dp.toPx(), near, 0.21f + random.nextFloat() * 0.52f)
            }
        }
        val sky = Brush.radialGradient(
            listOf(Color(0xFF291229), Color(0xFF12152B), Color(0xFF0B0E19)), center, w.coerceAtLeast(h) * 0.74f + 1f
        )
        val glow = Brush.radialGradient(
            listOf(Color(0x66FF823F), Color(0x36FA576B), Color.Transparent), center, radius * 2.8f
        )
        // Cached paths/brushes; the draw phase alone reads the motion state.
        val disk = Brush.linearGradient(
            listOf(Color(0xFF8B3963), Color(0xFFFF875B), Color(0xFFFFE3AF), Color(0xFFF57A4C)),
            center - Offset(radius * 2.2f, radius), center + Offset(radius * 2.2f, radius)
        )
        val diskSize = Size(radius * 4.55f, radius * 1.14f)
        val diskTopLeft = center - Offset(diskSize.width / 2f, diskSize.height / 2f)
        val edgeShade = Brush.horizontalGradient(
            0f to Color(0xDE0B0E19), 0.42f to Color(0x6A0B0E19), 0.67f to Color.Transparent
        )
        onDrawBehind {
            val angle = phase.floatValue
            drawRect(sky)
            clipRect {
                for (star in stars) {
                    val depth = (if (star.near) 8.dp else 2.dp).toPx()
                    val at = star.position + Offset(sin(angle) * depth, (cos(angle) - 1f) * depth * 0.45f)
                    drawCircle(Color(0xFFD8DFFF).copy(alpha = star.opacity), star.radius, at)
                }
                translate(sin(angle) * 2.dp.toPx(), sin(angle) * 1.dp.toPx()) {
                    drawCircle(glow, radius * 2.8f, center)
                    rotate(-13f, center) {
                        drawOval(disk, diskTopLeft, diskSize, alpha = 0.33f, style = Stroke(radius * 0.43f))
                        drawOval(disk, diskTopLeft, diskSize, style = Stroke(radius * 0.14f))
                        val lensSize = Size(radius * 2.55f, radius * 2.48f)
                        val lensOrigin = center - Offset(lensSize.width / 2f, lensSize.height * 0.56f)
                        drawArc(disk, 180f, 180f, false, lensOrigin, lensSize,
                            alpha = 0.55f, style = Stroke(radius * 0.063f))
                        drawCircle(Color(0xFFFFAF76), radius * 0.99f, center, alpha = 0.76f, style = Stroke(radius * 0.045f))
                        drawCircle(Color(0xFF040409), radius * 0.96f, center)
                        drawArc(disk, 0f, 180f, false, diskTopLeft, diskSize, style = Stroke(radius * 0.19f))
                        drawArc(disk, 0f, 180f, false, diskTopLeft + Offset(0f, radius * 0.10f), diskSize,
                            alpha = 0.54f, style = Stroke(radius * 0.055f))
                    }
                }
            }
            drawRect(edgeShade)
        }
    })
}

@Composable
private fun rememberHubMotionSetting(context: Context): Boolean {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var settingsAllow by remember(context, lifecycle) { mutableStateOf(false) }
    DisposableEffect(context, lifecycle) {
        val resolver = context.contentResolver
        val power = context.getSystemService(PowerManager::class.java)
        val key = Settings.Global.ANIMATOR_DURATION_SCALE
        fun refresh() {
            settingsAllow = runCatching {
                hubMotionAllowed(true, power?.isPowerSaveMode != false, Settings.Global.getFloat(resolver, key, 1f))
            }.onFailure { KenkernLog.w("Kenkern/Hub", "Animação decorativa desativada", it) }.getOrDefault(false)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED)
        }.onFailure { KenkernLog.w("Kenkern/Hub", "Não foi possível acompanhar a economia de energia", it) }.isSuccess
        val observerRegistered = runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }.onFailure { KenkernLog.w("Kenkern/Hub", "Não foi possível acompanhar a preferência de movimento", it) }.isSuccess
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && receiverRegistered && observerRegistered) refresh()
        }
        lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            if (receiverRegistered) context.unregisterReceiver(receiver)
            if (observerRegistered) resolver.unregisterContentObserver(observer)
        }
    }
    return windowFocused && settingsAllow
}

private data class Star(val position: Offset, val radius: Float, val near: Boolean, val opacity: Float)
private val TAU = (2 * PI).toFloat()
