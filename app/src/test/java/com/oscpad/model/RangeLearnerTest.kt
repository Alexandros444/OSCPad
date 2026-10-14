package com.oscpad.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RangeLearnerTest {

    private fun range(lo: Float, hi: Float, t: ValueType) = RangeLearner.range(lo, hi, t)

    @Test
    fun detectsTypes() {
        assertEquals(ValueType.INT, RangeLearner.valueTypeOf(5))
        assertEquals(ValueType.INT, RangeLearner.valueTypeOf(5L))
        assertEquals(ValueType.FLOAT, RangeLearner.valueTypeOf(0.5f))
        assertEquals(ValueType.FLOAT, RangeLearner.valueTypeOf(0.5))
        assertEquals(ValueType.BOOL, RangeLearner.valueTypeOf(true))
        assertEquals(null, RangeLearner.valueTypeOf("x"))
        assertEquals(null, RangeLearner.valueTypeOf(null))
    }

    @Test
    fun floatRanges() {
        assertEquals(0f to 1f, range(0.42f, 0.42f, ValueType.FLOAT))
        assertEquals(0f to 1f, range(0f, 1f, ValueType.FLOAT))
        assertEquals(0f to 1f, range(0f, 0f, ValueType.FLOAT))
        assertEquals(0f to 100f, range(75f, 75f, ValueType.FLOAT))
        assertEquals(-1f to 1f, range(-0.3f, -0.3f, ValueType.FLOAT))
        assertEquals(-1f to 1f, range(-1f, 1f, ValueType.FLOAT))
        assertEquals(-100f to 10f, range(-60f, 6f, ValueType.FLOAT))
        assertEquals(0f to 360f, range(0f, 270f, ValueType.FLOAT))
    }

    @Test
    fun intRanges() {
        assertEquals(0f to 1f, range(1f, 1f, ValueType.INT))
        assertEquals(0f to 127f, range(64f, 64f, ValueType.INT))
        assertEquals(0f to 127f, range(0f, 127f, ValueType.INT))
        assertEquals(0f to 255f, range(200f, 200f, ValueType.INT))
        assertEquals(0f to 16383f, range(0f, 9000f, ValueType.INT))
        assertEquals(-16383f to 16383f, range(-8192f, 8191f, ValueType.INT))
    }

    @Test
    fun boolIsAlwaysZeroToOne() {
        assertEquals(0f to 1f, range(1f, 1f, ValueType.BOOL))
    }

    @Test
    fun mergePrefersFloat() {
        assertEquals(ValueType.INT, RangeLearner.merge(null, ValueType.INT))
        assertEquals(ValueType.FLOAT, RangeLearner.merge(ValueType.INT, ValueType.FLOAT))
        assertEquals(ValueType.FLOAT, RangeLearner.merge(ValueType.FLOAT, ValueType.BOOL))
        assertEquals(ValueType.INT, RangeLearner.merge(ValueType.BOOL, ValueType.INT))
        assertEquals(ValueType.BOOL, RangeLearner.merge(ValueType.BOOL, null))
    }
}
