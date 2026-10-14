package com.oscpad.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class HistoryAndStepTest {

    @Test
    fun undoRedoWalksBackAndForth() {
        val h = History<String>()
        var state = "a"
        fun change(to: String) { h.record(state); state = to }
        change("b"); change("c")
        state = h.undo(state)!!; assertEquals("b", state)
        state = h.undo(state)!!; assertEquals("a", state)
        assertNull(h.undo(state))
        state = h.redo(state)!!; assertEquals("b", state)
        state = h.redo(state)!!; assertEquals("c", state)
        assertNull(h.redo(state))
    }

    @Test
    fun newChangeAfterUndoDropsRedo() {
        val h = History<Int>()
        h.record(1)
        assertEquals(1, h.undo(2))
        assertTrue(h.canRedo)
        h.record(1)
        assertFalse(h.canRedo)
    }

    @Test
    fun historyIsCapped() {
        val h = History<Int>(limit = 3)
        repeat(10) { h.record(it) }
        assertEquals(3, h.undoCount)
        assertEquals(9, h.undo(10))
    }

    @Test
    fun stepsFloatsExactly() {
        assertEquals("0.3", NumberStep.step("0.2", NumberStep.TENTH, up = true))
        assertEquals("0.9", NumberStep.step("1", NumberStep.TENTH, up = false))
        assertEquals("0", NumberStep.step("0.1", NumberStep.TENTH, up = false))
        assertEquals("-0.1", NumberStep.step("0", NumberStep.TENTH, up = false))
        assertEquals("0.35", NumberStep.step("0.25", NumberStep.TENTH, up = true))
        assertEquals("1.5", NumberStep.step("1,4", NumberStep.TENTH, up = true)) // comma decimal
    }

    @Test
    fun stepsIntsWithClamp() {
        assertEquals("8001", NumberStep.step("8000", NumberStep.ONE, up = true))
        assertEquals("65535", NumberStep.step("65535", NumberStep.ONE, up = true, max = BigDecimal(65535)))
        assertEquals("1", NumberStep.step("1", NumberStep.ONE, up = false, min = BigDecimal.ONE))
        assertEquals("1", NumberStep.step("", NumberStep.ONE, up = true)) // empty counts as 0
    }

    @Test
    fun cleansDisplayNumbers() {
        assertEquals("127", NumberStep.clean("127.0"))
        assertEquals("0.5", NumberStep.clean("0.50"))
        assertEquals("0", NumberStep.clean("-0.0"))
        assertEquals("abc", NumberStep.clean("abc"))
    }
}
