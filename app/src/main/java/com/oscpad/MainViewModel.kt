package com.oscpad

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oscpad.model.Control
import com.oscpad.model.Collision
import com.oscpad.model.ColorArgs
import com.oscpad.model.ControlType
import com.oscpad.model.FINE_GRID
import com.oscpad.model.GRID_OPTIONS
import com.oscpad.model.History
import com.oscpad.model.Layout
import com.oscpad.model.LayoutJson
import com.oscpad.model.Page
import com.oscpad.model.RangeLearner
import com.oscpad.model.ValueType
import com.oscpad.model.clampTo
import com.oscpad.model.defaultControl
import com.oscpad.model.demoLayout
import com.oscpad.model.layoutNameFromFileName
import com.oscpad.model.moved
import com.oscpad.model.newId
import com.oscpad.osc.ConnectionSettings
import com.oscpad.osc.ConnectionStatus
import com.oscpad.osc.MessageLog
import com.oscpad.osc.OscConnection
import com.oscpad.osc.OscDiscovery
import com.oscpad.osc.OscMessage
import com.oscpad.osc.asFloat
import com.oscpad.osc.displayArg
import com.oscpad.ui.AppTheme
import com.oscpad.ui.Themes
import com.oscpad.ui.UiStyle
import com.oscpad.ui.argbToHsv
import com.oscpad.ui.hsvToArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

enum class OrientationMode(val label: String, val activityFlag: Int) {
    AUTO("Auto-rotate", ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    LOCKED("Lock current", ActivityInfo.SCREEN_ORIENTATION_LOCKED),
}

/**
 * MIDI-learn style session: the first OSC address received (that wasn't already streaming
 * when learning started) is assigned to the control; later values on that address refine
 * its range and value type until the session ends.
 */
data class LearnSession(
    val controlId: String,
    val ignored: Set<String>,
    val address: String? = null,
    val lo: Float = Float.POSITIVE_INFINITY,
    val hi: Float = Float.NEGATIVE_INFINITY,
    val valueType: ValueType? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        /** Key suffix for the second (Y) value of XY pads in [values]. */
        const val Y_SUFFIX = "#y"

        /** Saturation and brightness of color wheels in [values]; the id key holds hue / 360. */
        const val S_SUFFIX = "#s"
        const val V_SUFFIX = "#v"

        /** Addresses seen this recently when Learn starts count as background traffic (meters, clocks). */
        private const val LEARN_IGNORE_WINDOW_MS = 1500L

        /** Incoming messages waiting for the UI thread beyond this are dropped (flood protection). */
        private const val INBOX_LIMIT = 4096

        val DEVICE_SERVICE_NAME = "OSCPad (${Build.MODEL})"
    }

    private val prefs = app.getSharedPreferences("oscpad", Context.MODE_PRIVATE)
    private val layoutFile = File(app.filesDir, "layout.json")
    private val mainHandler = Handler(Looper.getMainLooper())

    var layout by mutableStateOf(loadLayout())
        private set
    var pageIndex by mutableIntStateOf(0)
        private set
    var editMode by mutableStateOf(false)
        private set
    var selectedId by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(loadSettings())
        private set
    var orientation by mutableStateOf(
        OrientationMode.entries.firstOrNull { it.name == prefs.getString("orientation", null) } ?: OrientationMode.AUTO,
    )
        private set
    var gridDivisions by mutableIntStateOf(prefs.getInt("grid", FINE_GRID).takeIf { it in GRID_OPTIONS } ?: FINE_GRID)
        private set
    var theme: AppTheme by mutableStateOf(Themes.byId(prefs.getString("theme", null)))
        private set
    var uiStyle by mutableStateOf(UiStyle.entries.firstOrNull { it.name == prefs.getString("style", null) } ?: UiStyle.STANDARD)
        private set
    var announce by mutableStateOf(prefs.getBoolean("announce", true))
        private set
    var learn by mutableStateOf<LearnSession?>(null)
        private set

    /** Edit-mode drags stop at neighbouring controls instead of overlapping them. */
    var preventOverlap by mutableStateOf(prefs.getBoolean("preventOverlap", true))
        private set

    /** Last time each incoming address was seen (uptime ms), used to filter Learn noise. */
    private val lastSeen = HashMap<String, Long>()

    /** Normalized (0..1) control values keyed by control id; radios store their index. */
    val values = mutableStateMapOf<String, Float>()
    val labelTexts = mutableStateMapOf<String, String>()

    /** Recent traffic for the monitor; fixed size, so it never grows. */
    val log = MessageLog(capacity = 500)

    private val history = History<Layout>(limit = 200)
    private var historyVersion by mutableIntStateOf(0)
    val canUndo: Boolean get() = historyVersion.let { history.canUndo }
    val canRedo: Boolean get() = historyVersion.let { history.canRedo }

    private var addressIndex: Map<String, List<Control>> = emptyMap()
    private var saveJob: Job? = null

    // Incoming messages arrive on the network thread; they are queued and handled in one batch
    // per main-thread turn instead of posting a separate task for each message.
    private val inbox = ConcurrentLinkedQueue<OscMessage>()
    private val inboxSize = AtomicInteger()
    private val drainScheduled = AtomicBoolean(false)
    val droppedIncoming = AtomicLong()
    private val drainInbox = Runnable {
        drainScheduled.set(false)
        while (true) {
            val message = inbox.poll() ?: break
            inboxSize.decrementAndGet()
            onIncoming(message)
        }
    }

    private val connection = OscConnection { message ->
        if (inboxSize.incrementAndGet() > INBOX_LIMIT) {
            inboxSize.decrementAndGet()
            droppedIncoming.incrementAndGet()
            return@OscConnection
        }
        inbox.add(message)
        if (drainScheduled.compareAndSet(false, true)) mainHandler.post(drainInbox)
    }
    val connectionStatus: StateFlow<ConnectionStatus> get() = connection.status
    val txCounter: AtomicLong get() = connection.txCount
    val rxCounter: AtomicLong get() = connection.rxCount

    val discovery = OscDiscovery(app)

    val currentPage: Page get() = layout.pages[pageIndex.coerceIn(0, layout.pages.lastIndex)]
    val selectedControl: Control? get() = selectedId?.let { id -> currentPage.controls.find { it.id == id } }

    init {
        rebuildIndex()
        connection.configure(settings)
        if (announce) discovery.announce(DEVICE_SERVICE_NAME, settings.receivePort)
    }

    // ---- Performing -------------------------------------------------------------------------

    fun onControlValue(control: Control, value: Float, valueY: Float? = null) {
        val yKey = control.id + Y_SUFFIX
        // Skip repeats (e.g. a fader dragged within one value), but a button press must always
        // send, even if feedback already set the button to that state.
        val unchanged = values[control.id] == value && (valueY == null || values[yKey] == valueY)
        if (unchanged && control.type != ControlType.BUTTON) return
        values[control.id] = value
        if (valueY != null) values[yKey] = valueY
        val args: List<Any> = when (control.type) {
            ControlType.LABEL -> return
            ControlType.RADIO -> listOf(value.roundToInt())
            ControlType.XY -> listOf(control.output(value), control.output(values[yKey] ?: 0f))
            else -> listOf(control.output(value))
        }
        connection.send(control.address, args)
        log.add(outgoing = true, address = control.address, args = args)
    }

    /** Color wheels: hue in degrees, saturation and brightness 0..1. */
    fun onControlColor(control: Control, hue: Float, sat: Float, bright: Float) {
        val h = (hue / 360f).coerceIn(0f, 1f)
        val sKey = control.id + S_SUFFIX
        val vKey = control.id + V_SUFFIX
        if (values[control.id] == h && values[sKey] == sat && values[vKey] == bright) return
        values[control.id] = h
        values[sKey] = sat
        values[vKey] = bright
        val args = ColorArgs.encode(hsvToArgb(hue, sat, bright), control.colorFormat)
        connection.send(control.address, args)
        log.add(outgoing = true, address = control.address, args = args)
    }

    private fun onIncoming(message: OscMessage) {
        log.add(outgoing = false, address = message.address, args = message.args)
        learn?.let { learnFrom(it, message) }
        rememberSeen(message.address)
        val targets = addressIndex[message.address] ?: return
        val first = message.args.getOrNull(0)
        for (c in targets) {
            when (c.type) {
                ControlType.LABEL -> labelTexts[c.id] = message.args.joinToString(" ") { displayArg(it) }
                ControlType.COLOR -> ColorArgs.decode(message.args)?.let { argb ->
                    val hsv = argbToHsv(argb)
                    // Keep the previous hue for grays so the marker doesn't jump to red.
                    if (hsv[1] > 0f) values[c.id] = hsv[0] / 360f
                    values[c.id + S_SUFFIX] = hsv[1]
                    values[c.id + V_SUFFIX] = hsv[2]
                }
                ControlType.RADIO -> first.asFloat()?.let {
                    values[c.id] = it.roundToInt().coerceIn(0, c.steps - 1).toFloat()
                }
                ControlType.XY -> {
                    first.asFloat()?.let { values[c.id] = c.normalize(it) }
                    message.args.getOrNull(1).asFloat()?.let { values[c.id + Y_SUFFIX] = c.normalize(it) }
                }
                else -> first.asFloat()?.let { values[c.id] = c.normalize(it) }
            }
        }
    }

    fun clearLog() = log.clear()

    // ---- Learn ------------------------------------------------------------------------------

    fun toggleLearn() {
        if (learn != null) {
            learn = null
            return
        }
        val id = selectedId ?: return
        val now = SystemClock.uptimeMillis()
        val busy = lastSeen.filterValues { now - it < LEARN_IGNORE_WINDOW_MS }.keys.toSet()
        learn = LearnSession(controlId = id, ignored = busy)
    }

    private fun learnFrom(session: LearnSession, message: OscMessage) {
        val learned = session.address
        if (learned == null && message.address in session.ignored) return
        if (learned != null && message.address != learned) return
        val control = findControl(session.controlId) ?: run {
            learn = null
            return
        }

        val numeric = message.args.filter { RangeLearner.valueTypeOf(it) != null }
        val relevant = when (control.type) {
            ControlType.LABEL, ControlType.COLOR -> emptyList()
            ControlType.XY -> numeric.take(2)
            else -> numeric.take(1)
        }
        var lo = session.lo
        var hi = session.hi
        var type = session.valueType
        for (arg in relevant) {
            val v = arg.asFloat() ?: continue
            lo = minOf(lo, v)
            hi = maxOf(hi, v)
            type = RangeLearner.merge(type, RangeLearner.valueTypeOf(arg))
        }

        var updated = control.copy(address = message.address)
        if (control.type == ControlType.COLOR) {
            updated = updated.copy(colorFormat = ColorArgs.detect(message.args) ?: control.colorFormat)
        } else if (type != null) {
            updated = if (control.type == ControlType.RADIO) {
                // Radios send their index; widen the segment count to cover the indices seen.
                updated.copy(steps = maxOf(control.steps, hi.roundToInt() + 1).coerceIn(2, 32))
            } else {
                val (min, max) = RangeLearner.range(lo, hi, type)
                updated.copy(min = min, max = max, valueType = type)
            }
        }
        if (learned == null) recordUndo() // one undo step for the whole learn session
        learn = session.copy(address = message.address, lo = lo, hi = hi, valueType = type)
        if (updated != control) updateControl(updated)
    }

    private fun rememberSeen(address: String) {
        val now = SystemClock.uptimeMillis()
        if (lastSeen.size > 2000) lastSeen.entries.removeAll { now - it.value > LEARN_IGNORE_WINDOW_MS }
        lastSeen[address] = now
    }

    // ---- Undo / redo (history covers the current edit-mode session) --------------------------

    /** Call before a layout change that should be undoable as one step. */
    fun recordUndo() {
        history.record(layout)
        historyVersion++
    }

    fun undo() {
        history.undo(layout)?.let { restore(it) }
    }

    fun redo() {
        history.redo(layout)?.let { restore(it) }
    }

    private fun restore(snapshot: Layout) {
        learn = null
        layout = snapshot
        pageIndex = pageIndex.coerceIn(0, snapshot.pages.lastIndex)
        if (selectedId != null && selectedControl == null) selectedId = null
        historyVersion++
        onLayoutChanged()
    }

    // ---- Pages & editing --------------------------------------------------------------------

    fun select(id: String?) {
        if (learn != null && learn?.controlId != id) learn = null
        selectedId = id
    }

    fun selectPage(index: Int) {
        pageIndex = index.coerceIn(0, layout.pages.lastIndex)
        select(null)
    }

    fun toggleEditMode() {
        editMode = !editMode
        select(null)
        history.clear()
        historyVersion++
        if (!editMode) saveNow()
    }

    fun updatePreventOverlap(enabled: Boolean) {
        preventOverlap = enabled
        prefs.edit().putBoolean("preventOverlap", enabled).apply()
    }

    /** Where a new control goes: as asked, or the nearest free spot when overlap is prevented. */
    private fun placeNew(c: Control): Control =
        if (preventOverlap) Collision.freeSpot(c, currentPage.controls, FINE_GRID) else c

    fun updateGrid(divisions: Int) {
        gridDivisions = divisions
        prefs.edit().putInt("grid", divisions).apply()
    }

    private fun findControl(id: String): Control? =
        layout.pages.firstNotNullOfOrNull { page -> page.controls.find { it.id == id } }

    /** Replaces the control with the same id, on whichever page it lives (no undo step). */
    fun updateControl(updated: Control) {
        layout = layout.copy(
            pages = layout.pages.map { page ->
                if (page.controls.none { it.id == updated.id }) page
                else page.copy(controls = page.controls.map { if (it.id == updated.id) updated else it })
            },
        )
        onLayoutChanged()
    }

    /** [updateControl] as its own undo step, e.g. when saving the properties dialog. */
    fun commitControl(updated: Control) {
        if (findControl(updated.id) == updated) return
        recordUndo()
        updateControl(updated)
    }

    fun addControl(type: ControlType) {
        recordUndo()
        val n = currentPage.controls.count { it.type == type } + 1
        val control = placeNew(defaultControl(type, n))
        mutatePage { it.copy(controls = it.controls + control) }
        select(control.id)
    }

    fun duplicateSelected() {
        val s = selectedControl ?: return
        recordUndo()
        val copy = placeNew(
            s.copy(
                id = newId(),
                x = (s.x + 1f / 24f).clampTo(0f, 1f - s.w),
                y = (s.y + 1f / 24f).clampTo(0f, 1f - s.h),
            ),
        )
        mutatePage { it.copy(controls = it.controls + copy) }
        select(copy.id)
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        recordUndo()
        mutatePage { page -> page.copy(controls = page.controls.filterNot { it.id == id }) }
        select(null)
        values.remove(id)
        values.remove(id + Y_SUFFIX)
        values.remove(id + S_SUFFIX)
        values.remove(id + V_SUFFIX)
        labelTexts.remove(id)
    }

    fun addPage() {
        recordUndo()
        layout = layout.copy(pages = layout.pages + Page(name = "Page ${layout.pages.size + 1}"))
        pageIndex = layout.pages.lastIndex
        select(null)
        onLayoutChanged()
    }

    /** Moves a page (undoable); the moved page stays selected. Pass [recordUndo] = false while dragging. */
    fun movePage(from: Int, to: Int, recordUndo: Boolean = true) {
        if (from == to || from !in layout.pages.indices || to !in layout.pages.indices) return
        if (recordUndo) recordUndo()
        val selectedPageId = currentPage.id
        layout = layout.copy(pages = layout.pages.moved(from, to))
        pageIndex = layout.pages.indexOfFirst { it.id == selectedPageId }.coerceAtLeast(0)
        onLayoutChanged()
    }

    fun renameCurrentPage(name: String) {
        if (name == currentPage.name) return
        recordUndo()
        mutatePage { it.copy(name = name) }
    }

    fun deleteCurrentPage() {
        if (layout.pages.size <= 1) return
        recordUndo()
        val index = pageIndex
        layout = layout.copy(pages = layout.pages.filterIndexed { i, _ -> i != index })
        pageIndex = (index - 1).coerceAtLeast(0)
        select(null)
        onLayoutChanged()
    }

    fun loadDemo() = replaceLayout(demoLayout())

    private fun mutatePage(transform: (Page) -> Page) {
        val index = pageIndex
        layout = layout.copy(pages = layout.pages.mapIndexed { i, p -> if (i == index) transform(p) else p })
        onLayoutChanged()
    }

    private fun replaceLayout(newLayout: Layout) {
        layout = newLayout
        pageIndex = 0
        select(null)
        history.clear()
        historyVersion++
        values.clear()
        labelTexts.clear()
        onLayoutChanged()
    }

    private fun onLayoutChanged() {
        rebuildIndex()
        scheduleSave()
    }

    private fun rebuildIndex() {
        addressIndex = layout.pages.flatMap { it.controls }.groupBy { it.address }
    }

    // ---- Settings ---------------------------------------------------------------------------

    fun updateSettings(newSettings: ConnectionSettings) {
        val portChanged = newSettings.receivePort != settings.receivePort
        settings = newSettings
        prefs.edit()
            .putString("host", newSettings.host)
            .putInt("sendPort", newSettings.sendPort)
            .putInt("receivePort", newSettings.receivePort)
            .apply()
        connection.configure(newSettings)
        if (announce && portChanged) discovery.announce(DEVICE_SERVICE_NAME, newSettings.receivePort)
    }

    fun updateOrientation(mode: OrientationMode) {
        orientation = mode
        prefs.edit().putString("orientation", mode.name).apply()
    }

    fun updateStyle(style: UiStyle) {
        uiStyle = style
        prefs.edit().putString("style", style.name).apply()
        style.suggestedThemeId?.let { updateTheme(Themes.byId(it)) }
    }

    fun updateTheme(newTheme: AppTheme) {
        theme = newTheme
        prefs.edit().putString("theme", newTheme.id).apply()
    }

    fun updateAnnounce(enabled: Boolean) {
        announce = enabled
        prefs.edit().putBoolean("announce", enabled).apply()
        if (enabled) discovery.announce(DEVICE_SERVICE_NAME, settings.receivePort) else discovery.unannounce()
    }

    private fun loadSettings() = ConnectionSettings(
        host = prefs.getString("host", null) ?: ConnectionSettings().host,
        sendPort = prefs.getInt("sendPort", ConnectionSettings().sendPort),
        receivePort = prefs.getInt("receivePort", ConnectionSettings().receivePort),
    )

    // ---- Persistence, import & export ------------------------------------------------------

    private fun loadLayout(): Layout = try {
        if (layoutFile.exists()) LayoutJson.decode(layoutFile.readText()) else demoLayout()
    } catch (_: Exception) {
        demoLayout()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        val snapshot = layout
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            writeLayout(snapshot)
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        writeLayout(layout)
    }

    private fun writeLayout(l: Layout) {
        try {
            val tmp = File(layoutFile.path + ".tmp")
            tmp.writeText(LayoutJson.encode(l))
            tmp.renameTo(layoutFile)
        } catch (_: Exception) {
            // Best effort; the next edit retries.
        }
    }

    fun importLayout(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: error("Cannot open file")
                    val decoded = LayoutJson.decode(stream.bufferedReader().use { it.readText() })
                    layoutNameFromFileName(displayName(uri))?.let { decoded.copy(name = it) } ?: decoded
                }
            }
            result
                .onSuccess {
                    replaceLayout(it)
                    toast("Loaded layout \"${it.name}\"")
                }
                .onFailure { toast("Import failed: ${it.message}") }
        }
    }

    fun exportLayout(uri: Uri) {
        viewModelScope.launch {
            // The file name the user chose (the picker may have changed it) becomes the layout name.
            val fileName = withContext(Dispatchers.IO) { layoutNameFromFileName(displayName(uri)) }
            if (fileName != null && fileName != layout.name) {
                layout = layout.copy(name = fileName)
                scheduleSave()
            }
            val json = LayoutJson.encode(layout)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
                        ?: error("Cannot open file")
                    stream.use { it.write(json.toByteArray()) }
                }
            }
            toast(result.fold({ "Saved layout \"${layout.name}\"" }, { "Export failed: ${it.message}" }))
        }
    }

    /** File name shown by the document provider, or null if it doesn't say. */
    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun toast(message: String) {
        Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onCleared() {
        saveNow()
        mainHandler.removeCallbacks(drainInbox)
        discovery.release()
        connection.close()
    }
}
