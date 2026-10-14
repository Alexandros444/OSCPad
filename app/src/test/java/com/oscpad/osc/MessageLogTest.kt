package com.oscpad.osc

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLogTest {

    @Test
    fun keepsOnlyNewestUpToCapacity() {
        val log = MessageLog(capacity = 3)
        repeat(5) { log.add(outgoing = it % 2 == 0, address = "/m$it", args = listOf(it)) }
        assertEquals(listOf("/m4", "/m3", "/m2"), log.newestFirst().map { it.address })
        assertEquals(3L, log.outgoing) // 0, 2, 4
        assertEquals(2L, log.incoming)
    }

    @Test
    fun versionChangesOnEveryAddAndClear() {
        val log = MessageLog(capacity = 2)
        val v0 = log.version
        log.add(outgoing = true, address = "/a", args = emptyList())
        val v1 = log.version
        log.clear()
        assertEquals(v0 + 1, v1)
        assertEquals(v1 + 1, log.version)
        assertEquals(0, log.newestFirst().size)
    }

    @Test
    fun sequenceNumbersAreUnique() {
        val log = MessageLog(capacity = 10)
        repeat(10) { log.add(outgoing = false, address = "/x", args = emptyList()) }
        assertEquals(10, log.newestFirst().map { it.seq }.toSet().size)
    }
}
