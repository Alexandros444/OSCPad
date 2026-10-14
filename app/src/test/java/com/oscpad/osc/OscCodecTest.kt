package com.oscpad.osc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class OscCodecTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun encodesIntMessageWithPadding() {
        val encoded = OscCodec.encodeMessage("/a", listOf(1))
        val expected = bytes(
            '/'.code, 'a'.code, 0, 0,
            ','.code, 'i'.code, 0, 0,
            0, 0, 0, 1,
        )
        assertArrayEquals(expected, encoded)
    }

    @Test
    fun addressOfFourCharsGetsFullPadWord() {
        val encoded = OscCodec.encodeMessage("/abc", emptyList())
        // "/abc" + 4 NULs, then "," + 3 NULs
        assertEquals(12, encoded.size)
        assertEquals(0, encoded[4].toInt())
    }

    @Test
    fun roundTripsAllTypes() {
        val args = listOf(42, 0.5f, "hello", true, false, null, 1.25, 123456789012L, 'x')
        val encoded = OscCodec.encodeMessage("/test/all", args)
        assertEquals(0, encoded.size % 4)
        val decoded = OscCodec.decode(encoded).single()
        assertEquals("/test/all", decoded.address)
        assertEquals(args, decoded.args)
    }

    @Test
    fun roundTripsBlob() {
        val blob = bytes(1, 2, 3, 4, 5)
        val decoded = OscCodec.decode(OscCodec.encodeMessage("/b", listOf(blob, 7))).single()
        assertArrayEquals(blob, decoded.args[0] as ByteArray)
        assertEquals(7, decoded.args[1])
    }

    @Test
    fun decodesNestedBundles() {
        val m1 = OscCodec.encodeMessage("/one", listOf(1f))
        val m2 = OscCodec.encodeMessage("/two", listOf("x"))
        val inner = bundle(m2)
        val outer = bundle(m1, inner)
        val decoded = OscCodec.decode(outer)
        assertEquals(listOf("/one", "/two"), decoded.map { it.address })
        assertEquals(1f, decoded[0].args[0])
        assertEquals("x", decoded[1].args[0])
    }

    @Test
    fun ignoresGarbage() {
        assertTrue(OscCodec.decode(bytes(1, 2, 3, 4, 5, 6, 7, 8)).isEmpty())
        assertTrue(OscCodec.decode(ByteArray(0)).isEmpty())
        // Truncated message keeps whatever decoded cleanly
        val truncated = OscCodec.encodeMessage("/t", listOf(1, 2)).copyOf(14)
        assertEquals("/t", OscCodec.decode(truncated).single().address)
    }

    @Test
    fun decodesUsingLengthNotArraySize() {
        val msg = OscCodec.encodeMessage("/len", listOf(3))
        val buffer = msg.copyOf(1024)
        assertEquals(listOf<Any?>(3), OscCodec.decode(buffer, msg.size).single().args)
    }

    private fun bundle(vararg elements: ByteArray): ByteArray {
        val size = 16 + elements.sumOf { 4 + it.size }
        val buf = ByteBuffer.allocate(size)
        buf.put("#bundle".toByteArray()).put(0.toByte())
        buf.putLong(1L) // "immediately"
        for (e in elements) {
            buf.putInt(e.size)
            buf.put(e)
        }
        return buf.array()
    }
}
