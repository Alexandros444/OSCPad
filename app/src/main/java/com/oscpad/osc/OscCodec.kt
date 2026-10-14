package com.oscpad.osc

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.min

/** OSC 1.0 color argument (type tag r). Stored as Android-style ARGB; sent as RGBA bytes. */
data class OscColor(val argb: Int) {
    val rgba: Int get() = (argb shl 8) or ((argb ushr 24) and 0xFF)

    companion object {
        fun fromRgba(rgba: Int) = OscColor((rgba ushr 8) or ((rgba and 0xFF) shl 24))
    }
}

data class OscMessage(val address: String, val args: List<Any?>) {
    override fun toString(): String = buildString {
        append(address)
        for (a in args) append(' ').append(formatArg(a))
    }
}

/**
 * OSC 1.0 encoder/decoder.
 *
 * Supported argument types: i (Int), f (Float), s (String), b (ByteArray), h (Long),
 * d (Double), T/F (Boolean), N (null), c (Char), r (OscColor). Incoming bundles are flattened.
 */
object OscCodec {

    fun encodeMessage(address: String, args: List<Any?>): ByteArray {
        val out = ByteArrayOutputStream(64)
        writeString(out, address)
        val tags = StringBuilder(",")
        for (a in args) tags.append(typeTag(a))
        writeString(out, tags.toString())
        for (a in args) writeArg(out, a)
        return out.toByteArray()
    }

    /** Decodes a UDP payload (message or bundle) into a flat list of messages. Malformed data is skipped. */
    fun decode(data: ByteArray, length: Int = data.size): List<OscMessage> {
        val out = ArrayList<OscMessage>()
        try {
            decodePacket(data, 0, length, out, depth = 0)
        } catch (_: RuntimeException) {
            // Malformed packet: keep whatever was decoded before the error.
        }
        return out
    }

    private fun typeTag(a: Any?): Char = when (a) {
        null -> 'N'
        is Boolean -> if (a) 'T' else 'F'
        is Int -> 'i'
        is Float -> 'f'
        is Double -> 'd'
        is Long -> 'h'
        is String -> 's'
        is ByteArray -> 'b'
        is Char -> 'c'
        is OscColor -> 'r'
        else -> throw IllegalArgumentException("Unsupported OSC argument type: ${a::class.java.simpleName}")
    }

    private fun writeArg(out: ByteArrayOutputStream, a: Any?) {
        when (a) {
            is Int -> writeInt(out, a)
            is Float -> writeInt(out, a.toRawBits())
            is Double -> writeLong(out, a.toRawBits())
            is Long -> writeLong(out, a)
            is String -> writeString(out, a)
            is Char -> writeInt(out, a.code)
            is OscColor -> writeInt(out, a.rgba)
            is ByteArray -> {
                writeInt(out, a.size)
                out.write(a)
                repeat((4 - a.size % 4) % 4) { out.write(0) }
            }
            else -> Unit // null / Boolean carry no payload
        }
    }

    private fun writeString(out: ByteArrayOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        out.write(bytes)
        val padded = (bytes.size / 4 + 1) * 4 // at least one NUL terminator
        repeat(padded - bytes.size) { out.write(0) }
    }

    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v ushr 24)
        out.write(v ushr 16)
        out.write(v ushr 8)
        out.write(v)
    }

    private fun writeLong(out: ByteArrayOutputStream, v: Long) {
        writeInt(out, (v ushr 32).toInt())
        writeInt(out, v.toInt())
    }

    private fun decodePacket(data: ByteArray, offset: Int, length: Int, out: MutableList<OscMessage>, depth: Int) {
        if (length < 4 || depth > 8) return
        if (length >= 16 && String(data, offset, 7, Charsets.US_ASCII) == "#bundle") {
            val buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN)
            buf.position(offset + 16) // "#bundle\0" + 8-byte time tag
            while (buf.remaining() >= 4) {
                val size = buf.int
                if (size <= 0 || size > buf.remaining()) break
                decodePacket(data, buf.position(), size, out, depth + 1)
                buf.position(buf.position() + size)
            }
        } else {
            decodeMessage(data, offset, length)?.let(out::add)
        }
    }

    private fun decodeMessage(data: ByteArray, offset: Int, length: Int): OscMessage? {
        val buf = ByteBuffer.wrap(data, offset, length).order(ByteOrder.BIG_ENDIAN)
        val address = readString(buf) ?: return null
        if (!address.startsWith("/")) return null
        if (!buf.hasRemaining()) return OscMessage(address, emptyList())
        val tags = readString(buf)
        if (tags == null || !tags.startsWith(",")) return OscMessage(address, emptyList())

        val args = ArrayList<Any?>(tags.length - 1)
        try {
            readArgs(buf, tags, args)
        } catch (_: RuntimeException) {
            // Truncated payload: keep the arguments that decoded cleanly.
        }
        return OscMessage(address, args)
    }

    private fun readArgs(buf: ByteBuffer, tags: String, args: MutableList<Any?>) {
        for (i in 1 until tags.length) {
            when (tags[i]) {
                'i', 'm' -> args.add(buf.int)
                'r' -> args.add(OscColor.fromRgba(buf.int))
                'f' -> args.add(buf.float)
                's', 'S' -> args.add(readString(buf) ?: break)
                'b' -> {
                    val n = buf.int
                    require(n in 0..buf.remaining()) { "Bad blob size" }
                    val blob = ByteArray(n)
                    buf.get(blob)
                    args.add(blob)
                    skip(buf, (4 - n % 4) % 4)
                }
                'h', 't' -> args.add(buf.long)
                'd' -> args.add(buf.double)
                'c' -> args.add(buf.int.toChar())
                'T' -> args.add(true)
                'F' -> args.add(false)
                'N', 'I' -> args.add(null)
                '[', ']' -> Unit // arrays are flattened
                else -> break // unknown tag: payload size unknown, stop here
            }
        }
    }

    private fun readString(buf: ByteBuffer): String? {
        val arr = buf.array()
        val start = buf.position()
        var end = start
        while (end < buf.limit() && arr[end] != 0.toByte()) end++
        if (end >= buf.limit()) return null
        val s = String(arr, start, end - start, Charsets.UTF_8)
        val padded = ((end - start) / 4 + 1) * 4
        buf.position(min(start + padded, buf.limit()))
        return s
    }

    private fun skip(buf: ByteBuffer, n: Int) {
        buf.position(min(buf.position() + n, buf.limit()))
    }
}

fun formatArg(a: Any?): String = when (a) {
    null -> "nil"
    is String -> "\"$a\""
    is ByteArray -> "blob[${a.size}]"
    is OscColor -> String.format(Locale.US, "#%08X", a.rgba)
    is Float -> String.format(Locale.US, "%.4f", a)
    is Double -> String.format(Locale.US, "%.4f", a)
    else -> a.toString()
}

/** Human-friendly rendering used for label controls. */
fun displayArg(a: Any?): String = when (a) {
    is String -> a
    is Float -> String.format(Locale.US, "%.2f", a)
    is Double -> String.format(Locale.US, "%.2f", a)
    else -> formatArg(a)
}

fun Any?.asFloat(): Float? = when (this) {
    is Float -> this
    is Int -> toFloat()
    is Double -> toFloat()
    is Long -> toFloat()
    is Boolean -> if (this) 1f else 0f
    is String -> toFloatOrNull()
    else -> null
}
