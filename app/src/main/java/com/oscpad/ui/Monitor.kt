package com.oscpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oscpad.MainViewModel
import com.oscpad.osc.LogEntry
import com.oscpad.osc.OscColor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fixed monitor palette (independent of the app theme so columns always read the same). */
private object MonitorColors {
    val background = Color(0xFF0B0C0F)
    val header = Color(0xFF181A20)
    val headerText = Color(0xFF9AA0A8)
    val out = Color(0xFFFF9F1C)
    val inn = Color(0xFF2EC4B6)
    val outRow = Color(0x14FF9F1C)
    val inRow = Color(0x142EC4B6)
    val stripe = Color(0x0AFFFFFF)
    val time = Color(0xFF7D838C)
    val address = Color(0xFFECEFF4)
    val types = Color(0xFFB48EFF)
    val number = Color(0xFFA5D86E)
    val string = Color(0xFFF2C46D)
    val bool = Color(0xFF6FB6FF)
    val other = Color(0xFF9AA0A8)
    val button = Color(0xFF23262D)
}

private enum class DirectionFilter(val label: String) { ALL("All"), IN("In ←"), OUT("Out →") }

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())

/**
 * Table view of recent traffic. The log itself is a fixed ring buffer; this dialog only
 * copies it (a few times per second, while open and not paused) and formats visible rows.
 */
@Composable
fun MonitorDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var paused by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(DirectionFilter.ALL) }
    var entries by remember { mutableStateOf(vm.log.newestFirst()) }
    var stats by remember { mutableStateOf(Triple(0L, 0L, 0L)) }

    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8 } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(paused) {
        var seen = -1L
        while (!paused) {
            val version = vm.log.version
            // While scrolled down to read older rows, hold the view still (rows would shift).
            if (version != seen && atTop) {
                seen = version
                entries = vm.log.newestFirst()
            }
            stats = Triple(vm.log.incoming, vm.log.outgoing, vm.droppedIncoming.get())
            delay(150)
        }
    }
    val shown = remember(entries, filter) {
        when (filter) {
            DirectionFilter.ALL -> entries
            DirectionFilter.IN -> entries.filter { !it.outgoing }
            DirectionFilter.OUT -> entries.filter { it.outgoing }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(14.dp),
            color = MonitorColors.background,
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OSC monitor", color = MonitorColors.address, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "← ${stats.first}  → ${stats.second}" +
                            (if (stats.third > 0) "  dropped ${stats.third}" else "") +
                            "  ·  last ${vm.log.capacity} kept",
                        Modifier.weight(1f),
                        color = MonitorColors.headerText, style = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Chip("Close", selected = false, onClick = onDismiss)
                }
                Row(
                    Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DirectionFilter.entries.forEach { f ->
                        Chip(f.label, selected = filter == f) { filter = f }
                    }
                    Spacer(Modifier.width(8.dp))
                    Chip(if (paused) "Resume" else "Pause", selected = paused) { paused = !paused }
                    Chip("Clear", selected = false) { vm.clearLog() }
                }
                Row(Modifier.fillMaxWidth().background(MonitorColors.header, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    HeaderCell("Time", Modifier.width(104.dp))
                    HeaderCell("", Modifier.width(26.dp))
                    HeaderCell("Address", Modifier.weight(1.2f))
                    HeaderCell("Types", Modifier.width(64.dp))
                    HeaderCell("Values", Modifier.weight(1.6f))
                }
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No messages yet. Move a control or send OSC to this device.", color = MonitorColors.headerText)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    // No item keys: index 0 is always the newest row, so the list stays pinned to the top.
                    LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(shown.size) { index -> MonitorRow(shown[index], index) }
                    }
                    if (!atTop) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp)) {
                            Chip("↑ Newest", selected = true) { scope.launch { listState.scrollToItem(0) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier
            .background(if (selected) MonitorColors.out else MonitorColors.button, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (selected) Color.Black else MonitorColors.address,
        fontSize = 13.sp,
    )
}

@Composable
private fun RowScope.HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier, color = MonitorColors.headerText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MonitorRow(entry: LogEntry, index: Int) {
    val tint = if (entry.outgoing) MonitorColors.outRow else MonitorColors.inRow
    val dirColor = if (entry.outgoing) MonitorColors.out else MonitorColors.inn
    Row(
        Modifier
            .fillMaxWidth()
            .background(tint)
            .background(if (index % 2 == 1) MonitorColors.stripe else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(TimeFormat.format(Instant.ofEpochMilli(entry.time)), Modifier.width(104.dp), color = MonitorColors.time, style = Mono)
        Text(if (entry.outgoing) "→" else "←", Modifier.width(26.dp), color = dirColor, style = Mono, fontWeight = FontWeight.Bold)
        Text(entry.address, Modifier.weight(1.2f), color = MonitorColors.address, style = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(typeTags(entry.args), Modifier.width(64.dp), color = MonitorColors.types, style = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatValues(entry.args), Modifier.weight(1.6f), style = Mono, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun typeTags(args: List<Any?>): String = args.joinToString("") { a ->
    when (a) {
        null -> "N"
        is Boolean -> if (a) "T" else "F"
        is Int -> "i"
        is Float -> "f"
        is Double -> "d"
        is Long -> "h"
        is String -> "s"
        is ByteArray -> "b"
        is Char -> "c"
        is OscColor -> "r"
        else -> "?"
    }
}.ifEmpty { "–" }

private fun formatValues(args: List<Any?>): AnnotatedString = buildAnnotatedString {
    args.forEachIndexed { i, a ->
        if (i > 0) append("  ")
        when (a) {
            is Float -> withStyle(SpanStyle(color = MonitorColors.number)) { append(String.format(Locale.US, "%.4f", a)) }
            is Double -> withStyle(SpanStyle(color = MonitorColors.number)) { append(String.format(Locale.US, "%.4f", a)) }
            is Int, is Long -> withStyle(SpanStyle(color = MonitorColors.number)) { append(a.toString()) }
            is String -> withStyle(SpanStyle(color = MonitorColors.string)) { append("\"$a\"") }
            is Boolean -> withStyle(SpanStyle(color = MonitorColors.bool)) { append(a.toString()) }
            is ByteArray -> withStyle(SpanStyle(color = MonitorColors.other)) { append("blob[${a.size}]") }
            is OscColor -> {
                withStyle(SpanStyle(color = Color(a.argb or 0xFF000000.toInt()))) { append("■ ") }
                withStyle(SpanStyle(color = MonitorColors.number)) { append(String.format(Locale.US, "RGBA %08X", a.rgba)) }
            }
            null -> withStyle(SpanStyle(color = MonitorColors.other)) { append("nil") }
            else -> withStyle(SpanStyle(color = MonitorColors.other)) { append(a.toString()) }
        }
    }
}
