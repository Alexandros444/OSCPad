package com.oscpad.model

import java.math.BigDecimal

/** Up/down stepping for numeric text fields, in exact decimal arithmetic (0.1 + 0.2 = 0.3). */
object NumberStep {
    val ONE: BigDecimal = BigDecimal.ONE
    val TENTH: BigDecimal = BigDecimal("0.1")

    fun step(text: String, step: BigDecimal, up: Boolean, min: BigDecimal? = null, max: BigDecimal? = null): String {
        val current = text.trim().replace(',', '.').toBigDecimalOrNull() ?: BigDecimal.ZERO
        var next = if (up) current + step else current - step
        if (min != null && next < min) next = min
        if (max != null && next > max) next = max
        return format(next)
    }

    /** Tidies a number for display: "127.0" → "127", "0.50" → "0.5". Unparseable text is kept. */
    fun clean(text: String): String = text.trim().replace(',', '.').toBigDecimalOrNull()?.let(::format) ?: text

    private fun format(v: BigDecimal): String = if (v.signum() == 0) "0" else v.stripTrailingZeros().toPlainString()
}
