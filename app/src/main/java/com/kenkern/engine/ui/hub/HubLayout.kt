package com.kenkern.engine.ui.hub

/** Breakpoints use safe-area bounds, not display orientation or raw pixels. */
internal data class HubLayout(val columns: Int, val shortcutRow: Boolean, val heroHeightDp: Int) {
    companion object {
        fun forBounds(widthDp: Float, heightDp: Float, fontScale: Float): HubLayout {
            val textWidth = widthDp / fontScale.coerceAtLeast(1f)
            return HubLayout(
                columns = if (textWidth >= 660f) 2 else 1,
                shortcutRow = textWidth >= 340f,
                heroHeightDp = if (heightDp < 460f) 112 else 180
            )
        }
    }
}

/** Ceil the interval above 1000 / 30; never catch up by drawing bursts. */
internal const val HUB_FRAME_INTERVAL_MS = 34L

internal fun hubMotionAllowed(focused: Boolean, powerSave: Boolean, animatorScale: Float): Boolean =
    focused && !powerSave && animatorScale.isFinite() && animatorScale > 0f
