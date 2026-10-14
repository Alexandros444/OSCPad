package com.oscpad.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class ControlType(val label: String) {
    BUTTON("Button"),
    TOGGLE("Toggle"),
    FADER("Fader"),
    KNOB("Knob"),
    XY("XY pad"),
    RADIO("Radio"),
    LABEL("Label"),
    COLOR("Color wheel"),
}

/** How a color wheel sends its color. */
enum class ColorFormat(val label: String) {
    /** OSC 1.0 32-bit RGBA color (type tag r): Resolume, TouchOSC. */
    RGBA("Color (r)"),
    FLOATS("3 floats"),
    INTS("3 ints 0–255"),
    HEX("Hex string"),
}

val Palette: List<Int> = listOf(
    0xFFFF9F1C, 0xFF2EC4B6, 0xFFE71D36, 0xFF4CC9F0,
    0xFFB5E48C, 0xFFF72585, 0xFFFFD60A, 0xFFADB5BD,
).map { it.toInt() }

const val WHITE: Int = -1 // 0xFFFFFFFF

/** Finest edit-mode grid: the page is divided into this many cells per axis. */
const val FINE_GRID = 48

/** Selectable grid sizes (cells per page axis). All divide [FINE_GRID]; 0 turns snapping off. */
val GRID_OPTIONS = listOf(48, 24, 16, 12, 8, 0)

const val MIN_SIZE = 2f / FINE_GRID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/** Layout name for a file: "My Show.json" → "My Show"; null for an empty name. */
fun layoutNameFromFileName(fileName: String?): String? =
    fileName?.trim()?.let { if (it.endsWith(".json", ignoreCase = true)) it.dropLast(5) else it }?.trim()?.ifEmpty { null }

/** Returns a copy with the element at [from] moved to index [to]. */
fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Rounds a page fraction to the nearest grid line; [divisions] <= 0 disables snapping. */
fun snap(v: Float, divisions: Int = FINE_GRID): Float =
    if (divisions <= 0) v else (v * divisions).roundToInt() / divisions.toFloat()

/** OSC argument type a control sends. */
enum class ValueType(val label: String) {
    FLOAT("Float"),
    INT("Int"),
    BOOL("Bool"),
}

/** coerceIn that never throws when the range collapses. */
fun Float.clampTo(lo: Float, hi: Float): Float = coerceIn(lo, max(lo, hi))

/**
 * A control on a page. Position and size are fractions (0..1) of the page area, so a layout
 * scales to any phone or tablet screen. Faders and radios orient themselves by their shape.
 */
data class Control(
    val id: String = newId(),
    val type: ControlType,
    val name: String,
    val address: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: Int = Palette[0],
    val min: Float = 0f,
    val max: Float = 1f,
    val steps: Int = 4,
    val valueType: ValueType = ValueType.FLOAT,
    val colorFormat: ColorFormat = ColorFormat.RGBA,
) {
    fun normalize(v: Float): Float = if (max == min) 0f else ((v - min) / (max - min)).coerceIn(0f, 1f)

    fun denormalize(n: Float): Float = min + n * (max - min)

    /** The OSC argument sent for a normalized value. */
    fun output(n: Float): Any = when (valueType) {
        ValueType.FLOAT -> denormalize(n)
        ValueType.INT -> denormalize(n).roundToInt()
        ValueType.BOOL -> n >= 0.5f
    }

    fun format(n: Float): String = when (valueType) {
        ValueType.FLOAT -> String.format(Locale.US, "%.2f", denormalize(n))
        ValueType.INT -> denormalize(n).roundToInt().toString()
        ValueType.BOOL -> if (n >= 0.5f) "on" else "off"
    }
}

data class Page(
    val id: String = newId(),
    val name: String,
    val controls: List<Control> = emptyList(),
)

data class Layout(val name: String, val pages: List<Page>)

fun defaultControl(type: ControlType, index: Int): Control {
    val (w, h) = when (type) {
        ControlType.BUTTON, ControlType.TOGGLE -> 0.125f to 0.1875f
        ControlType.FADER -> 0.0833f to 0.5f
        ControlType.KNOB -> 0.125f to 0.25f
        ControlType.XY -> 0.3333f to 0.5f
        ControlType.RADIO -> 0.4167f to 0.125f
        ControlType.LABEL -> 0.2083f to 0.0833f
        ControlType.COLOR -> 0.25f to 0.4583f
    }
    return Control(
        type = type,
        name = "${type.label} $index",
        address = "/${type.name.lowercase()}$index",
        x = snap((1f - w) / 2f),
        y = snap((1f - h) / 2f),
        w = w,
        h = h,
        color = if (type == ControlType.LABEL) WHITE else Palette[(index - 1).mod(Palette.size)],
    )
}

fun demoLayout(): Layout {
    val mixer = buildList {
        for (i in 0 until 8) {
            val x = 0.02f + i * 0.1225f
            val color = Palette[i % Palette.size]
            add(Control(type = ControlType.FADER, name = "Ch ${i + 1}", address = "/1/fader${i + 1}",
                x = x, y = 0.03f, w = 0.1f, h = 0.72f, color = color))
            add(Control(type = ControlType.TOGGLE, name = "Mute", address = "/1/toggle${i + 1}",
                x = x, y = 0.78f, w = 0.1f, h = 0.19f, color = color))
        }
    }
    val pads = buildList {
        for (r in 0 until 4) for (c in 0 until 4) {
            val n = r * 4 + c + 1
            add(Control(type = ControlType.BUTTON, name = "Pad $n", address = "/2/push$n",
                x = 0.02f + c * 0.145f, y = 0.03f + r * 0.24f, w = 0.13f, h = 0.22f,
                color = Palette[r % Palette.size]))
        }
        add(Control(type = ControlType.XY, name = "XY", address = "/2/xy",
            x = 0.61f, y = 0.03f, w = 0.37f, h = 0.94f, color = Palette[3]))
    }
    val fx = buildList {
        val knobNames = listOf("Cutoff", "Resonance", "Drive", "Mix")
        knobNames.forEachIndexed { i, name ->
            add(Control(type = ControlType.KNOB, name = name, address = "/3/rotary${i + 1}",
                x = 0.02f + i * 0.2f, y = 0.05f, w = 0.17f, h = 0.36f, color = Palette[(i + 4) % Palette.size]))
        }
        add(Control(type = ControlType.LABEL, name = "OSCPad", address = "/3/label",
            x = 0.82f, y = 0.05f, w = 0.16f, h = 0.36f, color = WHITE))
        add(Control(type = ControlType.RADIO, name = "Mode", address = "/3/radio",
            x = 0.02f, y = 0.47f, w = 0.96f, h = 0.17f, color = Palette[1], steps = 5))
        add(Control(type = ControlType.FADER, name = "Master", address = "/3/master",
            x = 0.02f, y = 0.7f, w = 0.96f, h = 0.26f, color = Palette[0]))
    }
    return Layout(
        name = "Demo",
        pages = listOf(
            Page(name = "Mixer", controls = mixer),
            Page(name = "Pads", controls = pads),
            Page(name = "FX", controls = fx),
        ),
    )
}

object LayoutJson {
    private const val FORMAT = "oscpad-layout"

    fun encode(layout: Layout): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("name", layout.name)
        put("pages", JSONArray().apply { layout.pages.forEach { put(encodePage(it)) } })
    }.toString(2)

    fun decode(text: String): Layout {
        val root = JSONObject(text)
        val pagesJson = root.optJSONArray("pages")
            ?: throw IllegalArgumentException("Not an OSCPad layout (missing \"pages\")")
        val seenIds = HashSet<String>()
        val pages = (0 until pagesJson.length()).map { i -> decodePage(pagesJson.getJSONObject(i), seenIds) }
        require(pages.isNotEmpty()) { "Layout has no pages" }
        return Layout(root.optString("name", "Imported"), pages)
    }

    private fun encodePage(p: Page) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("controls", JSONArray().apply { p.controls.forEach { put(encodeControl(it)) } })
    }

    private fun encodeControl(c: Control) = JSONObject().apply {
        put("id", c.id)
        put("type", c.type.name.lowercase())
        put("name", c.name)
        put("address", c.address)
        put("x", round4(c.x))
        put("y", round4(c.y))
        put("w", round4(c.w))
        put("h", round4(c.h))
        put("color", String.format(Locale.US, "#%08X", c.color))
        put("min", c.min.toDouble())
        put("max", c.max.toDouble())
        if (c.type == ControlType.RADIO) put("steps", c.steps)
        put("valueType", c.valueType.name.lowercase())
        if (c.type == ControlType.COLOR) put("colorFormat", c.colorFormat.name.lowercase())
    }

    private fun decodePage(o: JSONObject, seenIds: MutableSet<String>): Page {
        val arr = o.optJSONArray("controls") ?: JSONArray()
        val controls = (0 until arr.length()).mapNotNull { i -> decodeControl(arr.getJSONObject(i), seenIds) }
        return Page(id = uniqueId(o.optString("id"), seenIds), name = o.optString("name", "Page"), controls = controls)
    }

    private fun decodeControl(o: JSONObject, seenIds: MutableSet<String>): Control? {
        val type = ControlType.entries.firstOrNull { it.name.equals(o.optString("type"), ignoreCase = true) }
            ?: return null
        val w = o.optDouble("w", 0.1).toFloat().coerceIn(MIN_SIZE, 1f)
        val h = o.optDouble("h", 0.1).toFloat().coerceIn(MIN_SIZE, 1f)
        return Control(
            id = uniqueId(o.optString("id"), seenIds),
            type = type,
            name = o.optString("name", type.label),
            address = o.optString("address", "/${type.name.lowercase()}"),
            x = o.optDouble("x", 0.0).toFloat().clampTo(0f, 1f - w),
            y = o.optDouble("y", 0.0).toFloat().clampTo(0f, 1f - h),
            w = w,
            h = h,
            color = parseColor(o.optString("color"), Palette[0]),
            min = o.optDouble("min", 0.0).toFloat(),
            max = o.optDouble("max", 1.0).toFloat(),
            steps = o.optInt("steps", 4).coerceIn(2, 32),
            valueType = ValueType.entries.firstOrNull { it.name.equals(o.optString("valueType"), ignoreCase = true) }
                ?: if (o.optBoolean("int", false)) ValueType.INT else ValueType.FLOAT, // "int" = v1 files
            colorFormat = ColorFormat.entries.firstOrNull { it.name.equals(o.optString("colorFormat"), ignoreCase = true) }
                ?: ColorFormat.RGBA,
        )
    }

    private fun uniqueId(candidate: String, seen: MutableSet<String>): String {
        val id = candidate.takeIf { it.isNotBlank() && it !in seen } ?: newId()
        seen += id
        return id
    }

    private fun parseColor(s: String?, fallback: Int): Int {
        val hex = s?.removePrefix("#") ?: return fallback
        val v = hex.toLongOrNull(16) ?: return fallback
        return when (hex.length) {
            6 -> (0xFF000000 or v).toInt()
            8 -> v.toInt()
            else -> fallback
        }
    }

    private fun round4(v: Float): Double = (v * 10000f).roundToInt() / 10000.0
}
