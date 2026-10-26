package com.example.romalala

import org.junit.Assert.*
import org.junit.Test

class CaptureGenerationTest {
    @Test fun resizeOrVisibilityChangeRejectsOldResults() {
        val session = CaptureGeneration()
        val old = session.snapshot()
        assertTrue(session.accepts(old))
        session.invalidate()
        assertFalse(session.accepts(old))
        assertTrue(session.accepts(session.snapshot()))
    }

    @Test fun stopCannotBeUndoneByALateResizeCallback() {
        val session = CaptureGeneration()
        val pending = session.snapshot()
        session.close()
        session.invalidate()
        assertFalse(session.accepts(pending))
        assertFalse(session.accepts(session.snapshot()))
    }
}
