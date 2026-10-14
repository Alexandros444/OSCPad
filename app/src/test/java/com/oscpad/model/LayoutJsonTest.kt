package com.oscpad.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutJsonTest {

    @Test
    fun roundTripsValueTypes() {
        val controls = ValueType.entries.mapIndexed { i, t ->
            Control(type = ControlType.FADER, name = "f$i", address = "/f$i", x = 0f, y = 0f, w = 0.1f, h = 0.1f, valueType = t)
        }
        val layout = Layout("t", listOf(Page(name = "p", controls = controls)))
        val decoded = LayoutJson.decode(LayoutJson.encode(layout))
        assertEquals(ValueType.entries.toList(), decoded.pages[0].controls.map { it.valueType })
    }

    @Test
    fun readsVersion1IntFlag() {
        val json = """{"pages":[{"name":"p","controls":[
            {"type":"fader","address":"/a","x":0,"y":0,"w":0.1,"h":0.1,"int":true},
            {"type":"fader","address":"/b","x":0,"y":0,"w":0.1,"h":0.1}
        ]}]}"""
        val types = LayoutJson.decode(json).pages[0].controls.map { it.valueType }
        assertEquals(listOf(ValueType.INT, ValueType.FLOAT), types)
    }

    @Test
    fun outputMatchesValueType() {
        val c = Control(type = ControlType.FADER, name = "", address = "/a", x = 0f, y = 0f, w = 0.1f, h = 0.1f, min = 0f, max = 127f)
        assertEquals(63.5f, c.output(0.5f))
        assertEquals(64, c.copy(valueType = ValueType.INT).output(0.5f))
        assertEquals(true, c.copy(valueType = ValueType.BOOL).output(0.5f))
        assertEquals(false, c.copy(valueType = ValueType.BOOL).output(0.2f))
    }

    @Test
    fun roundTripsColorWheel() {
        val c = Control(type = ControlType.COLOR, name = "c", address = "/c", x = 0f, y = 0f, w = 0.2f, h = 0.3f, colorFormat = ColorFormat.HEX)
        val decoded = LayoutJson.decode(LayoutJson.encode(Layout("t", listOf(Page(name = "p", controls = listOf(c)))))).pages[0].controls[0]
        assertEquals(ControlType.COLOR, decoded.type)
        assertEquals(ColorFormat.HEX, decoded.colorFormat)
    }
}
