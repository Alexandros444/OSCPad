@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.oscpad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oscpad.MainViewModel
import com.oscpad.OrientationMode
import com.oscpad.model.ColorFormat
import com.oscpad.model.Control
import com.oscpad.model.ControlType
import com.oscpad.model.NumberStep
import com.oscpad.model.Palette
import com.oscpad.model.ValueType
import com.oscpad.model.WHITE
import com.oscpad.osc.ConnectionSettings
import com.oscpad.osc.DiscoveredService
import com.oscpad.osc.OscDiscovery
import com.oscpad.osc.localIpv4Addresses
import java.math.BigDecimal

private fun parsePort(s: String): Int? = s.toIntOrNull()?.takeIf { it in 1..65535 }
private val PORT_MIN = BigDecimal(1)
private val PORT_MAX = BigDecimal(65535)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsDialog(vm: MainViewModel, onDismiss: () -> Unit, onImport: () -> Unit, onExport: () -> Unit) {
    val current = vm.settings
    var host by remember { mutableStateOf(current.host) }
    var sendPort by remember { mutableStateOf(current.sendPort.toString()) }
    var receivePort by remember { mutableStateOf(current.receivePort.toString()) }
    var confirmDemo by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    val status by vm.connectionStatus.collectAsState()
    val selfName by vm.discovery.selfName.collectAsState()
    val ips = remember { localIpv4Addresses() }
    val sp = parsePort(sendPort)
    val rp = parsePort(receivePort)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("Connection")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it.trim() },
                        label = { Text("Host IP") },
                        singleLine = true,
                        isError = host.isBlank(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { discovering = true }) { Text("Find…") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(
                        sendPort, { sendPort = it.filter(Char::isDigit).take(5) }, "Send port", NumberStep.ONE,
                        Modifier.weight(1f), integer = true, min = PORT_MIN, max = PORT_MAX, isError = sp == null,
                    )
                    NumberField(
                        receivePort, { receivePort = it.filter(Char::isDigit).take(5) }, "Receive port", NumberStep.ONE,
                        Modifier.weight(1f), integer = true, min = PORT_MIN, max = PORT_MAX, isError = rp == null,
                    )
                }
                Text(
                    "This device: " + ips.joinToString().ifEmpty { "no network connection" } +
                        "  ·  send feedback to the receive port",
                    style = MaterialTheme.typography.bodySmall,
                )
                val statusText = status.error ?: buildString {
                    append(if (status.targetResolved) "Sending to ${current.host}:${current.sendPort}" else "Not connected")
                    status.listeningPort?.let { append("  ·  listening on $it") }
                }
                Text(
                    statusText,
                    color = if (status.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Announce this device (Bonjour)")
                        Text(
                            if (vm.announce) "Visible to OSC apps as “${selfName ?: MainViewModel.DEVICE_SERVICE_NAME}” on port ${current.receivePort}"
                            else "Other apps can't discover this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vm.announce, onCheckedChange = vm::updateAnnounce)
                }

                HorizontalDivider()
                SectionTitle("Appearance")
                Text("Style", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    UiStyle.entries.forEach { s ->
                        FilterChip(selected = vm.uiStyle == s, onClick = { vm.updateStyle(s) }, label = { Text(s.label) })
                    }
                }
                Text(
                    "Retro terminal and Evangelion also switch to their matching colors; pick other colors below if you like.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Colors", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Themes.all.forEach { t ->
                        FilterChip(
                            selected = vm.theme.id == t.id,
                            onClick = { vm.updateTheme(t) },
                            label = { Text(t.name) },
                            leadingIcon = { ThemeDot(t) },
                        )
                    }
                }
                Text("Screen orientation", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrientationMode.entries.forEach { mode ->
                        FilterChip(
                            selected = vm.orientation == mode,
                            onClick = { vm.updateOrientation(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }

                HorizontalDivider()
                SectionTitle("Layout: ${vm.layout.name}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImport) { Text("Import…") }
                    OutlinedButton(onClick = onExport) { Text("Export…") }
                }
                TextButton(onClick = {
                    if (confirmDemo) {
                        vm.loadDemo()
                        onDismiss()
                    } else {
                        confirmDemo = true
                    }
                }) {
                    Text(if (confirmDemo) "Tap again to replace current layout" else "Load demo layout")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = sp != null && rp != null && host.isNotBlank(),
                onClick = {
                    vm.updateSettings(ConnectionSettings(host, sp!!, rp!!))
                    onDismiss()
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    if (discovering) {
        DiscoveryDialog(
            discovery = vm.discovery,
            onDismiss = { discovering = false },
            onPick = { service ->
                host = service.host.orEmpty()
                sendPort = service.port.toString()
                discovering = false
            },
        )
    }
}

@Composable
private fun ThemeDot(theme: AppTheme) {
    Box(
        Modifier.size(14.dp).clip(CircleShape).background(theme.background)
            .border(3.dp, theme.accent, CircleShape),
    )
}

/** Lists OSC services announced on the network; picking one fills host and send port. */
@Composable
fun DiscoveryDialog(discovery: OscDiscovery, onDismiss: () -> Unit, onPick: (DiscoveredService) -> Unit) {
    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val services by discovery.services.collectAsState()
    val searching by discovery.searching.collectAsState()
    val error by discovery.error.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OSC devices on the network") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    error ?: if (searching) "Searching for Bonjour services of type ${OscDiscovery.SERVICE_TYPE}…" else "Starting…",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                services.forEach { s ->
                    val ready = s.host != null
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = ready) { onPick(s) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(s.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                            if (s.isSelf) {
                                Text(
                                    "this device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        Text(
                            if (ready) "${s.host}  ·  port ${s.port}" else "resolving…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    "Only apps that announce OSC via Bonjour / Zeroconf appear here. " +
                        "Tap one to use its address and port as the send target. Anything else can be entered manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun ControlDialog(control: Control, onDismiss: () -> Unit, onSave: (Control) -> Unit) {
    var name by remember { mutableStateOf(control.name) }
    var address by remember { mutableStateOf(control.address) }
    var type by remember { mutableStateOf(control.type) }
    var min by remember { mutableStateOf(NumberStep.clean(control.min.toString())) }
    var max by remember { mutableStateOf(NumberStep.clean(control.max.toString())) }
    var steps by remember { mutableStateOf(control.steps.toString()) }
    var valueType by remember { mutableStateOf(control.valueType) }
    var colorFormat by remember { mutableStateOf(control.colorFormat) }
    var color by remember { mutableStateOf(control.color) }
    var showWheel by remember { mutableStateOf(control.color !in Palette && control.color != WHITE) }

    val minF = min.replace(',', '.').toFloatOrNull()
    val maxF = max.replace(',', '.').toFloatOrNull()
    val stepsI = steps.toIntOrNull()?.takeIf { it in 2..32 }
    val addressOk = address.startsWith("/") && address.none { it.isWhitespace() }
    val valid = addressOk && minF != null && maxF != null && (type != ControlType.RADIO || stepsI != null)
    val rangeStep = if (valueType == ValueType.INT) NumberStep.ONE else NumberStep.TENTH

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control properties") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, label = { Text("OSC address") },
                    singleLine = true, isError = !addressOk, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ControlType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
                if (type == ControlType.COLOR) {
                    Text("Sends", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ColorFormat.entries.forEach { f ->
                            FilterChip(selected = colorFormat == f, onClick = { colorFormat = f }, label = { Text(f.label) })
                        }
                    }
                }
                if (type != ControlType.LABEL && type != ControlType.RADIO && type != ControlType.COLOR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(min, { min = it }, "Min", rangeStep, Modifier.weight(1f), isError = minF == null)
                        NumberField(max, { max = it }, "Max", rangeStep, Modifier.weight(1f), isError = maxF == null)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Sends", style = MaterialTheme.typography.labelLarge)
                        ValueType.entries.forEach { t ->
                            FilterChip(selected = valueType == t, onClick = { valueType = t }, label = { Text(t.label) })
                        }
                    }
                }
                if (type == ControlType.RADIO) {
                    NumberField(
                        steps, { steps = it.filter(Char::isDigit).take(2) }, "Segments (2–32)", NumberStep.ONE,
                        integer = true, min = BigDecimal(2), max = BigDecimal(32), isError = stepsI == null,
                    )
                }
                Text("Color", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Palette + WHITE).forEach { c -> Swatch(Color(c), selected = c == color) { color = c } }
                    // Last swatch toggles the full color wheel.
                    Box(
                        Modifier.size(32.dp).clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                            .border(if (showWheel) 3.dp else 1.dp, if (showWheel) Color.White else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showWheel = !showWheel },
                    )
                }
                if (showWheel) ColorWheelPicker(color, onColorChange = { color = it })
                Text(typeHelp(type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        control.copy(
                            name = name,
                            address = address,
                            type = type,
                            min = minF ?: control.min,
                            max = maxF ?: control.max,
                            steps = stepsI ?: control.steps,
                            valueType = valueType,
                            colorFormat = colorFormat,
                            color = color,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    val base = Modifier.size(32.dp).clip(CircleShape).background(color)
    Box(
        (if (selected) base.border(3.dp, Color.White, CircleShape) else base.border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape))
            .clickable(onClick = onClick),
    )
}

private fun typeHelp(type: ControlType): String = when (type) {
    ControlType.BUTTON -> "Sends Max while pressed and Min on release (Bool: true / false)."
    ControlType.TOGGLE -> "Sends Max when switched on and Min when switched off (Bool: true / false)."
    ControlType.FADER -> "Sends a value from Min to Max. Make it wider than tall for a horizontal fader."
    ControlType.KNOB -> "Drag up or right to increase. Sends a value from Min to Max."
    ControlType.XY -> "Sends two values (x, y) in one message."
    ControlType.RADIO -> "Sends the selected segment index (0 … segments−1) as an integer."
    ControlType.LABEL -> "Shows its name. Messages received on its address replace the text."
    ControlType.COLOR -> "Pick hue and saturation on the wheel, brightness on the bar. “Color (r)” is the OSC color " +
        "type Resolume and TouchOSC use; Learn detects the format your software sends."
}

@Composable
fun RenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename page") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
