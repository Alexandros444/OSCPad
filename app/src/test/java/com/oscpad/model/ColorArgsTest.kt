package com.oscpad.model

import com.oscpad.osc.OscCodec
import com.oscpad.osc.OscColor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorArgsTest {

    private val orange = 0xFFFF8000.toInt()

    @Test
    fun oscColorIsSentAsRgbaBytes() {
        // Resolume shows the value as RGBA: red in the highest byte, alpha last.
        val bytes = OscCodec.encodeMessage("/c", listOf(OscColor(orange)))
        val payload = bytes.copyOfRange(bytes.size - 4, bytes.size)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00, 0xFF.toByte()), payload)
        assertEquals(',', bytes[4].toInt().toChar())
        assertEquals('r', bytes[5].toInt().toChar())
    }

    @Test
    fun oscColorRoundTrips() {
        val decoded = OscCodec.decode(OscCodec.encodeMessage("/c", listOf(OscColor(0x80123456.toInt())))).single()
        assertEquals(OscColor(0x80123456.toInt()), decoded.args[0])
    }

    @Test
    fun encodesEveryFormat() {
        assertEquals(listOf(OscColor(orange)), ColorArgs.encode(orange, ColorFormat.RGBA))
        assertEquals(listOf(1f, 128 / 255f, 0f), ColorArgs.encode(orange, ColorFormat.FLOATS))
        assertEquals(listOf(255, 128, 0), ColorArgs.encode(orange, ColorFormat.INTS))
        assertEquals(listOf("#FF8000"), ColorArgs.encode(orange, ColorFormat.HEX))
    }

    @Test
    fun decodesAnyForm() {
        assertEquals(orange, ColorArgs.decode(listOf(OscColor(0x00FF8000))))   // alpha 0 from host is ignored
        assertEquals(orange, ColorArgs.decode(listOf(1f, 0.502f, 0f)))
        assertEquals(orange, ColorArgs.decode(listOf(255, 128, 0)))
        assertEquals(orange, ColorArgs.decode(listOf("#ff8000")))
        assertEquals(orange, ColorArgs.decode(listOf("FF8000CC")))
        assertNull(ColorArgs.decode(listOf(0.5f)))
        assertNull(ColorArgs.decode(listOf("nope")))
    }

    @Test
    fun detectsFormatForLearn() {
        assertEquals(ColorFormat.RGBA, ColorArgs.detect(listOf(OscColor(0))))
        assertEquals(ColorFormat.FLOATS, ColorArgs.detect(listOf(1f, 0f, 0f, 1f)))
        assertEquals(ColorFormat.INTS, ColorArgs.detect(listOf(255, 0, 0)))
        assertEquals(ColorFormat.HEX, ColorArgs.detect(listOf("#00ff00")))
        assertNull(ColorArgs.detect(listOf(1f)))
    }

    @Test
    fun movesListItems() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
    }
}
