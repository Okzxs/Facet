package com.facet.music.core.ui.components

import org.junit.Assert.*
import org.junit.Test

class PlayerSliderStateTest {
    @Test fun cancelRestoresLatestPlayback() {
        val state = PlayerSliderState().start().change(0.8f).cancel()
        assertFalse(state.dragging); assertNull(state.preview)
        assertEquals(0.3f, state.display(0.3f), 0f)
    }
    @Test fun stopPreservesPreviewUntilCommit() {
        val state = PlayerSliderState().start().change(0.8f).stop()
        assertFalse(state.dragging); assertEquals(0.8f, state.preview!!, 0f)
    }
    @Test fun cancelThenMaterialFinishHasNoSeekValue() {
        assertNull(PlayerSliderState().start().change(0.8f).cancel().stop().preview)
    }
    @Test fun accessibilityChangeDoesNotEnterDrag() {
        val state = PlayerSliderState().change(0.5f)
        assertFalse(state.dragging); assertEquals(0.5f, state.preview!!, 0f)
        assertNull(state.cancel().preview)
    }
    @Test fun newTrackStartsWithoutOldPreview() {
        val old = PlayerSliderState().start().change(0.9f)
        val fresh = PlayerSliderState()
        assertNotEquals(old, fresh); assertEquals(0.1f, fresh.display(0.1f), 0f)
    }
    @Test fun previewClampsToRange() {
        assertEquals(0f, PlayerSliderState().change(-1f).preview!!, 0f)
        assertEquals(1f, PlayerSliderState().change(2f).preview!!, 0f)
    }
    @Test fun rtlMirrorsTrackAndEndpoints() {
        assertEquals(0f, trackX(0f, 200f, false), 0f)
        assertEquals(200f, trackX(0f, 200f, true), 0f)
        assertEquals(200f, trackX(1f, 200f, false), 0f)
        assertEquals(0f, trackX(1f, 200f, true), 0f)
        assertEquals(150f, trackX(0.25f, 200f, true), 0f)
    }
    @Test fun rtlActiveAndBufferShareGeometry() {
        assertEquals(200f, trackX(0.3f, 200f, true) + trackX(0.3f, 200f, false), 0f)
        assertTrue(trackX(0.8f, 200f, true) < trackX(0.3f, 200f, true))
    }
}
