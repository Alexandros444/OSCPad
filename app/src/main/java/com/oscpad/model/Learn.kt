package com.oscpad.model

/**
 * Infers a control's OSC value type and min/max range from values received while learning.
 * A single value can only give a guess (e.g. 0.42 → 0..1, int 64 → 0..127); moving the source
 * through its full range while learning refines it.
 */
object RangeLearner {

    /** Common upper bounds for float parameters: normalized, percent, MIDI, byte, degrees… */
    private val FLOAT_BOUNDS = floatArrayOf(1f, 10f, 100f, 127f, 255f, 360f, 1000f, 10_000f, 100_000f, 1_000_000f)

    /** Common upper bounds for integer parameters: on/off, 7-bit, 8-bit, 10-bit, 12-bit, 14-bit, 16-bit. */
    private val INT_BOUNDS = floatArrayOf(1f, 127f, 255f, 1023f, 4095f, 16383f, 65535f, 2_147_483_647f)

    fun valueTypeOf(arg: Any?): ValueType? = when (arg) {
        is Int, is Long -> ValueType.INT
        is Float, is Double -> ValueType.FLOAT
        is Boolean -> ValueType.BOOL
        else -> null
    }

    /** Float wins over int (a host that ever sends floats wants floats); bool only if nothing numeric. */
    fun merge(a: ValueType?, b: ValueType?): ValueType? = when {
        a == null -> b
        b == null || a == b -> a
        a == ValueType.FLOAT || b == ValueType.FLOAT -> ValueType.FLOAT
        else -> ValueType.INT
    }

    /** Min/max covering the observed values [lo]..[hi], rounded out to a conventional range. */
    fun range(lo: Float, hi: Float, type: ValueType): Pair<Float, Float> {
        if (type == ValueType.BOOL) return 0f to 1f
        val bounds = if (type == ValueType.INT) INT_BOUNDS else FLOAT_BOUNDS
        fun up(v: Float): Float = bounds.firstOrNull { it >= v } ?: v
        return when {
            lo >= 0f -> 0f to up(hi)
            hi <= 0f -> up(-lo).let { -it to it } // only negatives seen: assume bipolar (pan, pitch)
            else -> -up(-lo) to up(hi)
        }
    }
}
