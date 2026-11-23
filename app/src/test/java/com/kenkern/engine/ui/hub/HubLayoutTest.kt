package com.kenkern.engine.ui.hub

import com.kenkern.engine.editor.EditorWorkspace
import com.kenkern.engine.editor.PanelStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubLayoutTest {
    @Test fun `compact multi-window and large fonts keep one readable column`() {
        assertEquals(HubLayout(1, false, 180), HubLayout.forBounds(320f, 620f, 1f))
        assertEquals(HubLayout(1, false, 112), HubLayout.forBounds(632f, 340f, 2f))
        assertEquals(HubLayout(1, true, 180), HubLayout.forBounds(390f, 740f, 1f))
    }

    @Test fun `short landscape uses shallow hero and a two column grid when wide enough`() {
        assertEquals(HubLayout(2, true, 112), HubLayout.forBounds(780f, 320f, 1f))
    }

    @Test fun `decorative clock respects focus battery and reduced motion and caps at 30 fps`() {
        assertTrue(hubMotionAllowed(true, false, 1f))
        assertFalse(hubMotionAllowed(false, false, 1f))
        assertFalse(hubMotionAllowed(true, true, 1f))
        assertFalse(hubMotionAllowed(true, false, 0f))
        assertFalse(hubMotionAllowed(true, false, Float.NaN))
        assertTrue(HUB_FRAME_INTERVAL_MS * 30 >= 1000)
    }

    @Test fun `workspaces keep independent panel layouts and honor saved overrides`() {
        val keys = EditorWorkspace.entries.map { it.panelKey("hierarchy") }
        assertEquals("hierarchy", keys.first()) // preserve old preferences
        assertEquals(3, keys.distinct().size)
        val showHierarchy = EditorWorkspace.ANIMATE.hierarchyByDefault
        assertFalse(PanelStateStore.parseLinha(null, 140f, 320f, showHierarchy).visible)
        assertTrue(PanelStateStore.parseLinha("1,1,200,16,16,280,340", 140f, 320f, showHierarchy).visible)
    }
}
