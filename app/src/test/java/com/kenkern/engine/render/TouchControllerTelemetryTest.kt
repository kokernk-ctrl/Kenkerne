package com.kenkern.engine.render

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

class TouchControllerTelemetryTest {

    @Test
    fun `deslocamento final do drag continua publicado depois do ACTION_UP`() {
        val debug = ViewportDebugState()
        val controller = TouchController(
            camera = OrbitCamera(),
            tapSlopPx = 10f,
            debug = debug
        )

        controller.onTouch(event(MotionEvent.ACTION_DOWN, 0L, 100f, 100f))
        controller.onTouch(event(MotionEvent.ACTION_MOVE, 50L, 180f, 160f))
        assertEquals(100f, debug.touchMovedPx, 0.001f)

        controller.onTouch(event(MotionEvent.ACTION_UP, 70L, 180f, 160f))
        assertEquals(100f, debug.touchMovedPx, 0.001f)
        assertEquals(TouchClass.DRAG, debug.touchClass)
    }

    @Test
    fun `UP sem MOVE mede o deslocamento final pelo MotionEvent`() {
        val debug = ViewportDebugState()
        var taps = 0
        val controller = TouchController(
            camera = OrbitCamera(),
            tapSlopPx = 10f,
            onTap = { _, _ -> taps++ },
            debug = debug
        )

        controller.onTouch(event(MotionEvent.ACTION_DOWN, 0L, 100f, 100f))
        controller.onTouch(event(MotionEvent.ACTION_UP, 50L, 130f, 140f))

        assertEquals(50f, debug.touchMovedPx, 0.001f)
        assertEquals(TouchClass.DRAG, debug.touchClass)
        assertEquals("deslocamento acima do slop não seleciona mesmo sem ACTION_MOVE", 0, taps)
    }

    private fun event(action: Int, timeMs: Long, x: Float, y: Float): MotionEvent {
        val event = mock(MotionEvent::class.java)
        Mockito.`when`(event.actionMasked).thenReturn(action)
        Mockito.`when`(event.eventTime).thenReturn(timeMs)
        Mockito.`when`(event.pointerCount).thenReturn(1)
        Mockito.`when`(event.actionIndex).thenReturn(0)
        Mockito.`when`(event.getX(0)).thenReturn(x)
        Mockito.`when`(event.getY(0)).thenReturn(y)
        return event
    }
}
