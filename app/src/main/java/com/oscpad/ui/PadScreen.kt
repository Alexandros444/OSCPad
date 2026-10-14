package com.oscpad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oscpad.LearnSession
import com.oscpad.MainViewModel
import com.oscpad.model.Control
import com.oscpad.model.Collision
import com.oscpad.model.ControlType
import com.oscpad.model.FINE_GRID
import com.oscpad.model.GRID_OPTIONS
import com.oscpad.model.Snapper
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun OscPadApp(vm: MainViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var showMonitor by remember { mutableStateOf(false) }
    var renamingPage by remember { mutableStateOf(false) }
    var editingControl by remember { mutableStateOf<Control?>(null) }
    val theme = LocalAppTheme.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importLayout)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportLayout)
    }

    // Surface provides the default content color for icons and text.
    Surface(Modifier.fillMaxSize(), color = theme.background, contentColor = theme.text) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(vm, onSettings = { showSettings = true }, onMonitor = { showMonitor = true })
            PageCanvas(
                vm,
                onEditControl = { editingControl = it },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(6.dp),
            )
            if (vm.editMode) {
                EditToolbar(
                    vm,
                    onProperties = { editingControl = vm.selectedControl },
                    onRenamePage = { renamingPage = true },
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            vm,
            onDismiss = { showSettings = false },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
            onExport = {
                val fileName = vm.layout.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").ifBlank { "layout" }
                exportLauncher.launch("$fileName.json")
            },
        )
    }
    if (showMonitor) MonitorDialog(vm, onDismiss = { showMonitor = false })
    editingControl?.let { control ->
        ControlDialog(
            control,
            onDismiss = { editingControl = null },
            onSave = {
                vm.commitControl(it)
                editingControl = null
            },
        )
    }
    if (renamingPage) {
        RenameDialog(
            initial = vm.currentPage.name,
            onDismiss = { renamingPage = false },
            onSave = {
                vm.renameCurrentPage(it)
                renamingPage = false
            },
        )
    }
}

// ---- Header ---------------------------------------------------------------------------------

/**
 * Wide screens: status on the left, page buttons centered on screen, actions on the right
 * (both sides get the same width so the center really is the center). Narrow screens move the
 * page buttons to their own centered row.
 */
@Composable
private fun TopBar(vm: MainViewModel, onSettings: () -> Unit, onMonitor: () -> Unit) {
    val theme = LocalAppTheme.current
    val style = LocalUiStyle.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(theme.bar).drawBehind { headerEdge(style, theme) }) {
        if (maxWidth >= 600.dp) {
            val side = 184.dp
            Row(Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusArea(vm, onSettings, Modifier.width(side), showTarget = true)
                PageTabs(vm, Modifier.weight(1f))
                ActionButtons(vm, onSettings, onMonitor, Modifier.width(side))
            }
        } else {
            Column {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusArea(vm, onSettings, Modifier.weight(1f), showTarget = false)
                    ActionButtons(vm, onSettings, onMonitor, Modifier)
                }
                PageTabs(vm, Modifier.fillMaxWidth().height(60.dp))
            }
        }
    }
}

/** Bottom edge of the header: hazard stripes (Evangelion), phosphor line (terminal), hairline (minimal). */
private fun DrawScope.headerEdge(style: UiStyle, theme: AppTheme) {
    when (style) {
        UiStyle.NERV -> {
            val h = 5.dp.toPx()
            val top = size.height - h
            drawRect(theme.accent.copy(alpha = 0.85f), Offset(0f, top), Size(size.width, h))
            clipRect(0f, top, size.width, size.height) {
                val step = 12.dp.toPx()
                var x = -h
                while (x < size.width) {
                    drawLine(theme.background, Offset(x, size.height), Offset(x + h, top), 5.dp.toPx())
                    x += step
                }
            }
        }
        UiStyle.TERMINAL -> drawLine(theme.accent.copy(alpha = 0.5f), Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 2f)
        UiStyle.MINIMAL -> drawLine(theme.outline, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 1f)
        UiStyle.STANDARD -> Unit
    }
}

@Composable
private fun StatusArea(vm: MainViewModel, onSettings: () -> Unit, modifier: Modifier, showTarget: Boolean) {
    val theme = LocalAppTheme.current
    val status by vm.connectionStatus.collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ActivityLed("TX", vm.txCounter, theme.accent)
        ActivityLed("RX", vm.rxCounter, theme.secondary)
        if (showTarget) {
            Text(
                "${vm.settings.host}:${vm.settings.sendPort}",
                Modifier.clickable(interactionSource = null, indication = null, onClick = onSettings).padding(horizontal = 6.dp),
                color = if (status.error != null) theme.error else theme.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Page buttons, centered (scrolling when they don't fit). In edit mode a long press picks a
 * page up and dragging reorders it live; the whole drag is one undo step.
 */
@Composable
private fun PageTabs(vm: MainViewModel, modifier: Modifier) {
    // Tab extents in the row's coordinates, by page id.
    val bounds = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    var dragId by remember { mutableStateOf<String?>(null) }
    val dragX = remember { mutableFloatStateOf(0f) }
    var grab by remember { mutableFloatStateOf(0f) }
    val reorder = if (!vm.editMode) Modifier else Modifier.pointerInput(vm) {
        detectDragGesturesAfterLongPress(
            onDragStart = { p ->
                val hit = bounds.entries.firstOrNull { p.x >= it.value.first && p.x <= it.value.second }
                if (hit != null) {
                    dragId = hit.key
                    grab = p.x - hit.value.first
                    dragX.floatValue = p.x
                    vm.recordUndo()
                    vm.selectPage(vm.layout.pages.indexOfFirst { it.id == hit.key })
                }
            },
            onDrag = { change, amount ->
                val id = dragId
                if (id != null) {
                    change.consume()
                    dragX.floatValue += amount.x
                    val pages = vm.layout.pages
                    val index = pages.indexOfFirst { it.id == id }
                    val own = bounds[id]
                    if (index >= 0 && own != null) {
                        val center = dragX.floatValue - grab + (own.second - own.first) / 2f
                        val next = pages.getOrNull(index + 1)?.let { bounds[it.id] }
                        val prev = pages.getOrNull(index - 1)?.let { bounds[it.id] }
                        when {
                            next != null && center > (next.first + next.second) / 2f -> vm.movePage(index, index + 1, recordUndo = false)
                            prev != null && center < (prev.first + prev.second) / 2f -> vm.movePage(index, index - 1, recordUndo = false)
                        }
                    }
                }
            },
            onDragEnd = { dragId = null },
            onDragCancel = { dragId = null },
        )
    }
    // Box centers the row when it fits; when it doesn't, the row scrolls.
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp).then(reorder),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            vm.layout.pages.forEachIndexed { i, page ->
                key(page.id) {
                    val dragging = dragId == page.id
                    PageTab(
                        page.name,
                        selected = i == vm.pageIndex,
                        dragging = dragging,
                        modifier = Modifier
                            .onPlaced { c ->
                                val x = c.positionInParent().x
                                bounds[page.id] = x to x + c.size.width
                            }
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer {
                                // Follow the finger; read in the layer block so only this tab redraws.
                                translationX = if (dragging) dragX.floatValue - grab - (bounds[page.id]?.first ?: 0f) else 0f
                            },
                    ) { vm.selectPage(i) }
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(vm: MainViewModel, onSettings: () -> Unit, onMonitor: () -> Unit, modifier: Modifier) {
    val theme = LocalAppTheme.current
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::toggleEditMode, modifier = Modifier.size(52.dp)) {
            Icon(
                if (vm.editMode) Icons.Filled.Done else Icons.Filled.Edit,
                contentDescription = if (vm.editMode) "Finish editing" else "Edit layout",
                tint = if (vm.editMode) theme.accent else theme.text,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onMonitor, modifier = Modifier.size(52.dp)) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "OSC monitor", tint = theme.text, modifier = Modifier.size(28.dp))
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(52.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = theme.text, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun PageTab(name: String, selected: Boolean, dragging: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val theme = LocalAppTheme.current
    val style = LocalUiStyle.current
    val shape = style.chromeShape
    val idle = if (style == UiStyle.TERMINAL) Color.Transparent else theme.tabIdle
    val framed = style != UiStyle.STANDARD
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 96.dp)
            .clip(shape)
            .background(if (selected) theme.accent else idle)
            .then(
                if (framed || dragging) Modifier.border(if (dragging) 2.dp else 1.dp, if (dragging) theme.secondary else theme.accent.copy(alpha = 0.55f), shape)
                else Modifier,
            )
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            style.tabText(name),
            color = if (selected) theme.onAccent else theme.text,
            fontSize = if (style == UiStyle.NERV) 18.sp else 16.sp,
            fontWeight = if (selected || style == UiStyle.NERV) FontWeight.SemiBold else FontWeight.Normal,
            letterSpacing = if (style == UiStyle.MINIMAL) 1.sp else 0.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Blinks while traffic flows. Reads its state in the draw phase, so it only redraws. */
@Composable
private fun ActivityLed(label: String, counter: AtomicLong, color: Color) {
    val theme = LocalAppTheme.current
    val lit = remember { mutableStateOf(false) }
    LaunchedEffect(counter) {
        var last = counter.get()
        while (true) {
            delay(100)
            val now = counter.get()
            lit.value = now != last
            last = now
        }
    }
    Row(Modifier.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Led(lit, color, theme.outline)
        Spacer(Modifier.width(5.dp))
        Text(label, color = theme.muted, fontSize = 11.sp)
    }
}

@Composable
private fun Led(lit: State<Boolean>, on: Color, off: Color) {
    val round = LocalUiStyle.current.let { it == UiStyle.STANDARD || it == UiStyle.MINIMAL }
    Box(
        Modifier.size(10.dp).drawBehind {
            if (round) drawCircle(if (lit.value) on else off) else drawRect(if (lit.value) on else off)
        },
    )
}

// ---- Page canvas & editing ------------------------------------------------------------------

/** Alignment guides shown while dragging, as page fractions (x = vertical lines). */
private data class Guides(val xs: List<Float> = emptyList(), val ys: List<Float> = emptyList())

@Composable
private fun PageCanvas(vm: MainViewModel, onEditControl: (Control) -> Unit, modifier: Modifier) {
    val page = vm.currentPage
    val editing = vm.editMode
    var guides by remember { mutableStateOf(Guides()) }
    BoxWithConstraints(
        modifier.then(if (editing) Modifier.editGestures(vm, onEditControl, onGuides = { guides = it }) else Modifier),
    ) {
        PageDecoration(page.controls, overlay = false)
        if (editing) GridBackground(vm.gridDivisions)
        page.controls.forEach { control ->
            key(control.id) {
                val placement = Modifier
                    .offset(x = maxWidth * control.x, y = maxHeight * control.y)
                    .size(width = maxWidth * control.w, height = maxHeight * control.h)
                    .padding(3.dp)
                ControlView(control, vm, interactive = !editing, modifier = placement)
                if (editing) {
                    EditOutline(
                        selected = control.id == vm.selectedId,
                        learning = control.id == vm.learn?.controlId,
                        modifier = placement,
                    )
                }
            }
        }
        PageDecoration(page.controls, overlay = true)
        if (editing && (guides.xs.isNotEmpty() || guides.ys.isNotEmpty())) GuideLines(guides)
        if (page.controls.isEmpty()) {
            Text(
                if (editing) "Tap “Add” to place controls on this page" else "Empty page — tap the pencil to edit",
                Modifier.align(Alignment.Center),
                color = LocalAppTheme.current.muted,
            )
        }
    }
}

/**
 * Edit-mode gestures, handled at page level so coordinates stay stable while a control moves:
 * tap selects, drag moves, dragging the selected control's corner resizes, double-tap opens
 * its properties. Dragged edges snap to other controls' edges and centers (shown as guides),
 * otherwise to the grid chosen in the toolbar. Each drag is one undo step.
 */
@Composable
private fun Modifier.editGestures(
    vm: MainViewModel,
    onEditControl: (Control) -> Unit,
    onGuides: (Guides) -> Unit,
): Modifier {
    val onEdit by rememberUpdatedState(onEditControl)
    val showGuides by rememberUpdatedState(onGuides)
    return this.pointerInput(vm) {
        var lastTapId: String? = null
        var lastTapTime = 0L
        awaitEachGesture {
            val down = awaitFirstDown()
            val pw = size.width.toFloat().coerceAtLeast(1f)
            val ph = size.height.toFloat().coerceAtLeast(1f)
            val p = down.position
            val selected = vm.selectedControl
            val handle = 28.dp.toPx()
            val resizing = selected != null &&
                abs(p.x - (selected.x + selected.w) * pw) < handle &&
                abs(p.y - (selected.y + selected.h) * ph) < handle
            val target = if (resizing) selected else vm.currentPage.controls.lastOrNull {
                p.x >= it.x * pw && p.x <= (it.x + it.w) * pw && p.y >= it.y * ph && p.y <= (it.y + it.h) * ph
            }
            vm.select(target?.id)
            if (target == null) return@awaitEachGesture
            down.consume()

            val others = vm.currentPage.controls.filter { it.id != target.id }
            val grid = vm.gridDivisions
            val tolX = 8.dp.toPx() / pw
            val tolY = 8.dp.toPx() / ph
            // Overlap-safe: neighbours the control doesn't already overlap act as walls.
            val obstacles = if (vm.preventOverlap) Collision.obstacles(target, others) else emptyList()
            var lastValid: Control = target
            var moved = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                    if (!change.pressed) break
                    val total: Offset = change.position - p
                    if (!moved && total.getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                        vm.recordUndo()
                    }
                    if (!moved) continue
                    change.consume()
                    val dx = total.x / pw
                    val dy = total.y / ph
                    val snapped = if (resizing) {
                        Snapper.resize(target, dx, dy, others, grid, tolX, tolY)
                    } else {
                        Snapper.move(target, dx, dy, others, grid, tolX, tolY)
                    }.control
                    val placed = when {
                        obstacles.isEmpty() -> snapped
                        resizing -> Collision.resize(lastValid, snapped, obstacles)
                        else -> Collision.move(lastValid, snapped, obstacles)
                    }
                    lastValid = placed
                    val result = Snapper.guidesFor(placed, others, resizing)
                    showGuides(Guides(result.guidesX, result.guidesY))
                    val current = vm.currentPage.controls.find { it.id == target.id } ?: break
                    vm.updateControl(current.copy(x = placed.x, y = placed.y, w = placed.w, h = placed.h))
                }
            } finally {
                showGuides(Guides())
            }

            if (!moved && !resizing) {
                val now = down.uptimeMillis
                if (lastTapId == target.id && now - lastTapTime < 350) {
                    lastTapId = null
                    vm.currentPage.controls.find { it.id == target.id }?.let { onEdit(it) }
                } else {
                    lastTapId = target.id
                    lastTapTime = now
                }
            }
        }
    }
}

/** The style's static page decoration (behind or over the controls); recorded once per change. */
@Composable
private fun PageDecoration(controls: List<Control>, overlay: Boolean) {
    val theme = LocalAppTheme.current
    val skin = LocalUiStyle.current.skin
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val cells = controls.map { Rect(it.x * size.width, it.y * size.height, (it.x + it.w) * size.width, (it.y + it.h) * size.height) }
            onDrawBehind { with(skin) { if (overlay) pageOverlay(theme) else pageBackground(theme, cells) } }
        },
    )
}

@Composable
private fun GridBackground(divisions: Int) {
    if (divisions <= 0) return
    val dot = LocalAppTheme.current.text.copy(alpha = 0.16f)
    Canvas(Modifier.fillMaxSize()) {
        // Thin out dots that would sit closer than 6dp, so fine grids stay readable on phones.
        val minSpacing = 6.dp.toPx()
        val w = size.width
        val h = size.height
        val stepX = ceil(minSpacing / (w / divisions)).toInt().coerceAtLeast(1)
        val stepY = ceil(minSpacing / (h / divisions)).toInt().coerceAtLeast(1)
        val points = buildList {
            for (i in 0..divisions step stepX) for (j in 0..divisions step stepY) {
                add(Offset(w * i / divisions, h * j / divisions))
            }
        }
        drawPoints(points, PointMode.Points, dot, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
private fun GuideLines(guides: Guides) {
    val color = LocalAppTheme.current.guide
    Canvas(Modifier.fillMaxSize()) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        val stroke = 1.5.dp.toPx()
        for (gx in guides.xs) {
            val x = gx * size.width
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke, pathEffect = dash)
        }
        for (gy in guides.ys) {
            val y = gy * size.height
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke, pathEffect = dash)
        }
    }
}

@Composable
private fun EditOutline(selected: Boolean, learning: Boolean, modifier: Modifier) {
    val theme = LocalAppTheme.current
    val shape = if (LocalUiStyle.current == UiStyle.STANDARD) RoundedCornerShape(10.dp) else RectangleShape
    Box(
        modifier.border(
            if (selected || learning) 2.dp else 1.dp,
            when {
                learning -> theme.secondary
                selected -> theme.text
                else -> theme.text.copy(alpha = 0.3f)
            },
            shape,
        ),
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 8.dp, y = 8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(theme.text)
                    .border(4.dp, Color.Black.copy(alpha = 0.45f), CircleShape),
            )
        }
    }
}

// ---- Edit toolbar ---------------------------------------------------------------------------

@Composable
private fun EditToolbar(vm: MainViewModel, onProperties: () -> Unit, onRenamePage: () -> Unit) {
    val theme = LocalAppTheme.current
    var addMenu by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    var gridMenu by remember { mutableStateOf(false) }
    val hasSelection = vm.selectedControl != null
    val learn = vm.learn
    val style = LocalUiStyle.current
    val shape = if (style == UiStyle.STANDARD) ButtonDefaults.outlinedShape else style.chromeShape
    Column(Modifier.fillMaxWidth().background(theme.bar)) {
        if (learn != null) LearnStatus(vm, learn)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(shape = shape, onClick = vm::undo, enabled = vm.canUndo) { Text("↶ Undo") }
            OutlinedButton(shape = shape, onClick = vm::redo, enabled = vm.canRedo) { Text("Redo ↷") }
            ToolbarDivider()
            Box {
                FilledTonalButton(shape = shape, onClick = { addMenu = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    ControlType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                addMenu = false
                                vm.addControl(type)
                            },
                        )
                    }
                }
            }
            if (learn == null) {
                OutlinedButton(shape = shape, onClick = vm::toggleLearn, enabled = hasSelection) { Text("Learn") }
            } else {
                Button(
                    shape = shape,
                    onClick = vm::toggleLearn,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.secondary, contentColor = Color.Black),
                ) { Text(if (learn.address == null) "Cancel learn" else "Done") }
            }
            OutlinedButton(shape = shape, onClick = onProperties, enabled = hasSelection) { Text("Properties") }
            OutlinedButton(shape = shape, onClick = vm::duplicateSelected, enabled = hasSelection) { Text("Duplicate") }
            OutlinedButton(shape = shape, onClick = vm::deleteSelected, enabled = hasSelection) { Text("Delete") }
            ToolbarDivider()
            Box {
                OutlinedButton(shape = shape, onClick = { gridMenu = true }) {
                    Text(if (vm.gridDivisions > 0) "Grid ${vm.gridDivisions}" else "Grid off")
                }
                DropdownMenu(expanded = gridMenu, onDismissRequest = { gridMenu = false }) {
                    GRID_OPTIONS.forEach { divisions ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (divisions) {
                                        0 -> "Off (guides only)"
                                        FINE_GRID -> "$divisions × $divisions (fine)"
                                        else -> "$divisions × $divisions"
                                    },
                                    color = if (divisions == vm.gridDivisions) theme.accent else Color.Unspecified,
                                )
                            },
                            onClick = {
                                gridMenu = false
                                vm.updateGrid(divisions)
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text((if (vm.preventOverlap) "✓  " else "     ") + "Prevent overlap") },
                        onClick = {
                            gridMenu = false
                            vm.updatePreventOverlap(!vm.preventOverlap)
                        },
                    )
                }
            }
            Box {
                OutlinedButton(shape = shape, onClick = { pageMenu = true }) { Text("Page…") }
                DropdownMenu(expanded = pageMenu, onDismissRequest = { pageMenu = false }) {
                    DropdownMenuItem(text = { Text("Add page") }, onClick = { pageMenu = false; vm.addPage() })
                    DropdownMenuItem(text = { Text("Rename page") }, onClick = { pageMenu = false; onRenamePage() })
                    DropdownMenuItem(
                        text = { Text("Move page left") },
                        enabled = vm.pageIndex > 0,
                        onClick = { pageMenu = false; vm.movePage(vm.pageIndex, vm.pageIndex - 1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move page right") },
                        enabled = vm.pageIndex < vm.layout.pages.lastIndex,
                        onClick = { pageMenu = false; vm.movePage(vm.pageIndex, vm.pageIndex + 1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete page") },
                        enabled = vm.layout.pages.size > 1,
                        onClick = { pageMenu = false; vm.deleteCurrentPage() },
                    )
                }
            }
            Text(
                "Tap: select · Drag: move · Drag corner: resize · Double-tap: properties · Long-press a page tab to reorder",
                color = theme.muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ToolbarDivider(height: Dp = 28.dp) {
    Box(Modifier.width(1.dp).height(height).background(LocalAppTheme.current.outline))
}

@Composable
private fun LearnStatus(vm: MainViewModel, learn: LearnSession) {
    val theme = LocalAppTheme.current
    val control = vm.layout.pages.firstNotNullOfOrNull { p -> p.controls.find { it.id == learn.controlId } }
    val text = if (learn.address == null) {
        val ignored = when (learn.ignored.size) {
            0 -> ""
            1 -> "  (ignoring 1 address that was already sending)"
            else -> "  (ignoring ${learn.ignored.size} addresses that were already sending)"
        }
        "Learning “${control?.name}”: move the parameter in your OSC software…$ignored"
    } else {
        val range = control?.let {
            when (it.type) {
                ControlType.LABEL -> ""
                ControlType.RADIO -> "  ·  ${it.steps} segments"
                else -> "  ·  ${it.valueType.label} ${it.format(0f)} … ${it.format(1f)}"
            }
        }.orEmpty()
        "Learned ${learn.address}$range  —  sweep the full range to refine, then tap Done"
    }
    Text(
        text,
        Modifier.fillMaxWidth().background(theme.secondary.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
        color = theme.secondary,
        fontSize = 13.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
