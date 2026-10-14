package com.oscpad.model

import com.oscpad.osc.OscColor
import java.util.Locale

/** Converts colors to and from OSC arguments in the formats a color wheel can use. */
object ColorArgs {

    private const val OPAQUE = 0xFF000000.toInt()

    fun encode(argb: Int, format: ColorFormat): List<Any> {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return when (format) {
            ColorFormat.RGBA -> listOf(OscColor(argb or OPAQUE))
            ColorFormat.FLOATS -> listOf(r / 255f, g / 255f, b / 255f)
            ColorFormat.INTS -> listOf(r, g, b)
            ColorFormat.HEX -> listOf(String.format(Locale.US, "#%06X", argb and 0xFFFFFF))
        }
    }

    /** Reads a color from incoming arguments in any supported form, or null. */
    fun decode(args: List<Any?>): Int? {
        when (val first = args.firstOrNull()) {
            is OscColor -> return first.argb or OPAQUE
            is String -> return parseHex(first)
        }
        val rgb = args.take(3)
        if (rgb.size < 3 || !rgb.all { it is Number }) return null
        val ints = rgb.all { it is Int || it is Long }
        val ch = rgb.map { n ->
            val v = (n as Number).toFloat()
            ((if (ints) v / 255f else v).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        }
        return OPAQUE or (ch[0] shl 16) or (ch[1] shl 8) or ch[2]
    }

    /** The format a host uses, judged from one of its messages (for Learn). */
    fun detect(args: List<Any?>): ColorFormat? {
        val first = args.firstOrNull()
        return when {
            first is OscColor -> ColorFormat.RGBA
            first is String -> if (parseHex(first) != null) ColorFormat.HEX else null
            args.size >= 3 && args.take(3).all { it is Int || it is Long } -> ColorFormat.INTS
            args.size >= 3 && args.take(3).all { it is Number } -> ColorFormat.FLOATS
            else -> null
        }
    }

    /** "#RRGGBB" or "#RRGGBBAA" (alpha ignored), with or without the hash. */
    fun parseHex(text: String): Int? {
        val hex = text.trim().removePrefix("#")
        val rgb = when (hex.length) {
            6 -> hex
            8 -> hex.substring(0, 6)
            else -> return null
        }
        return rgb.toLongOrNull(16)?.let { OPAQUE or it.toInt() }
    }
}
