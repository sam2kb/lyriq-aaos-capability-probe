package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.AudioInventory
import com.sam2kb.lyriqprobe.core.model.LmsNetworkSnapshot
import com.sam2kb.lyriqprobe.core.model.NetworkInventory
import com.sam2kb.lyriqprobe.core.model.UsbInventory
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Green
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun NetworkScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val connectivity = report?.connectivity
    val lms = report?.lmsNetwork
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning
    var host by rememberSaveable { mutableStateOf("") }
    var httpPort by rememberSaveable { mutableStateOf("9000") }
    var slimprotoPort by rememberSaveable { mutableStateOf("3483") }
    val httpPortValue = httpPort.toIntOrNull()
    val slimprotoPortValue = slimprotoPort.toIntOrNull()
    val portsValid = httpPortValue != null && httpPortValue in 1..65_535 &&
        slimprotoPortValue != null && slimprotoPortValue in 1..65_535

    ScreenScroll {
        PageHeader(
            kicker = "Connectivity / LMS",
            title = "Network & LMS readiness",
            description = "Connectivity inventory plus the two TCP paths a SqueezeClient port needs: the LMS " +
                "HTTP API and the local-player Slimproto port. The entered host is not written to the report.",
            aside = {
                if (lms != null) {
                    SmallTag(
                        when {
                            lms.lastTest.any { it.connected } -> "LMS REACHABLE"
                            lms.lastTest.isNotEmpty() -> "LMS FAILED"
                            else -> "LMS UNTESTED"
                        },
                        when {
                            lms.lastTest.any { it.connected } -> Green
                            lms.lastTest.isNotEmpty() -> Red
                            else -> Amber
                        },
                    )
                }
            },
        )

        if (connectivity == null) {
            EmptyNotice("Run Quick Scan to inventory connectivity and LMS network policy.")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Run Quick Scan",
                viewModel::runQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning,
            )
            return@ScreenScroll
        }

        Panel(title = "LMS endpoint probe", kicker = "SqueezeClient port path", accent = Teal) {
            if (lms == null) {
                Text(
                    "LMS network policy has not been scanned. Rerun Quick Scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                LmsPolicy(lms)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("LMS host or IP") },
                    placeholder = { Text("e.g. 192.168.1.20 or lms.local") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = httpPort,
                        onValueChange = { httpPort = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("HTTP port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = slimprotoPort,
                        onValueChange = { slimprotoPort = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Slimproto port") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    label = if (state.activeBusy != null) "Network test busy" else "Test LMS ports",
                    onClick = {
                        if (httpPortValue != null && slimprotoPortValue != null) {
                            viewModel.testLms(host, httpPortValue, slimprotoPortValue)
                        }
                    },
                    enabled = canAct && host.isNotBlank() && portsValid,
                    primary = true,
                )
                if (!portsValid) {
                    Spacer(Modifier.height(5.dp))
                    Text("Ports must be 1–65535.", style = MaterialTheme.typography.labelSmall, color = Amber)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "This opens two short TCP connections and closes them. It does not send LMS credentials, " +
                        "stream audio, or save the hostname/IP. The port probe is active only while parked.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
                if (lms.lastTest.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    lms.lastTest.forEach { endpoint ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${endpoint.label} : ${endpoint.port}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Paper,
                            )
                            Text(
                                if (endpoint.connected) {
                                    "CONNECTED · ${endpoint.latencyMs} ms"
                                } else {
                                    "${endpoint.errorClass ?: "FAILED"} · ${endpoint.errorMessage ?: ""}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (endpoint.connected) Teal else Red,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Endpoint kind: ${lms.endpointKind ?: "not tested"} · HTTP cleartext permitted: " +
                        "${lms.cleartextTrafficPermitted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Bluetooth", kicker = "Adapter only", accent = Teal) {
            KeyValue("Feature", connectivity.bluetooth.feature.toString())
            KeyValue("Adapter", connectivity.bluetooth.adapterAvailable.toString())
            KeyValue("Enabled", connectivity.bluetooth.enabled?.toString() ?: "unknown")
            KeyValue("State", connectivity.bluetooth.state ?: "unknown")
            KeyValue("Permission", connectivity.bluetooth.permissionState)
            val btError = connectivity.bluetooth.error
            if (btError != null) KeyValue("Error", btError, valueColor = Red)
            Spacer(Modifier.height(5.dp))
            Text(
                "No paired-device list, local name, or address is collected.",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Wi-Fi and networks", kicker = "Transports only", accent = Teal) {
            KeyValue("Wi-Fi feature", connectivity.wifi.feature.toString())
            KeyValue("Wi-Fi enabled", connectivity.wifi.enabled?.toString() ?: "unknown")
            val wifiError = connectivity.wifi.error
            if (wifiError != null) KeyValue("Wi-Fi error", wifiError, valueColor = Red)
            Spacer(Modifier.height(8.dp))
            NetworkPanel(connectivity.network)
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Location and GNSS", kicker = "No fix requested", accent = Teal) {
            KeyValue("GNSS feature", connectivity.location.featureGps.toString())
            KeyValue("Location enabled", connectivity.location.locationEnabled?.toString() ?: "unknown")
            val locationError = connectivity.location.error
            if (locationError != null) KeyValue("Error", locationError, valueColor = Red)
            Spacer(Modifier.height(8.dp))
            connectivity.location.providers.forEach { provider ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(provider.name, style = MaterialTheme.typography.bodySmall, color = Paper)
                    Text(
                        "enabled=${provider.enabled} accuracy=${provider.accuracy} power=${provider.powerUsage}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                "Provider metadata only. No location fix, coordinates, SSID, or IP address is collected.",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "USB", kicker = "UsbManager", accent = Teal) {
            UsbPanel(connectivity.usb)
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Displays", kicker = "DisplayManager", accent = Teal) {
            connectivity.displays.forEach { display ->
                KeyValue(
                    "Display ${display.displayId}",
                    "${display.name}: ${display.widthPx}x${display.heightPx} @ ${display.densityDpi}dpi " +
                        "${display.refreshRate}Hz rotation=${display.rotation} HDR=${display.hdrTypes}",
                )
            }
            if (connectivity.displays.isEmpty()) {
                Text("No displays reported.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Audio routes", kicker = "AudioManager", accent = Teal) {
            AudioPanel(connectivity.audio)
        }
        Spacer(Modifier.height(14.dp))

        Text(
            connectivity.privacyNote,
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun LmsPolicy(lms: LmsNetworkSnapshot) {
    KeyValue("INTERNET permission", lms.internetPermissionGranted.toString())
    KeyValue("ACCESS_LOCAL_NETWORK defined", lms.localNetworkPermissionDefined.toString())
    KeyValue("ACCESS_LOCAL_NETWORK granted", lms.localNetworkPermissionGranted.toString())
    KeyValue("NEARBY_WIFI_DEVICES defined", lms.nearbyWifiPermissionDefined.toString())
    KeyValue("Cleartext HTTP permitted", lms.cleartextTrafficPermitted.toString())
    Spacer(Modifier.height(5.dp))
    Text(lms.note, style = MaterialTheme.typography.labelSmall, color = Faint)
}

@Composable
private fun NetworkPanel(network: NetworkInventory) {
    val networkError = network.error
    if (networkError != null) {
        Text(networkError, style = MaterialTheme.typography.bodySmall, color = Red)
        return
    }
    Text(
        "${network.networkCount} network(s)",
        style = MaterialTheme.typography.bodySmall,
        color = Paper,
    )
    network.networks.forEach { summary ->
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                summary.transport.joinToString("/").ifBlank { "UNKNOWN" },
                style = MaterialTheme.typography.labelSmall,
                color = Teal,
            )
            Text(
                summary.capabilities.joinToString(","),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun UsbPanel(usb: UsbInventory) {
    KeyValue("Host feature", usb.hostFeature.toString())
    KeyValue("Manager", usb.managerAvailable.toString())
    KeyValue("Devices", usb.deviceCount.toString())
    val usbError = usb.error
    if (usbError != null) Text(usbError, style = MaterialTheme.typography.bodySmall, color = Red)
    usb.devices.forEach { device ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "VID 0x${device.vendorId.toString(16)} / PID 0x${device.productId.toString(16)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Paper,
            )
            Text(
                "class=${device.deviceClass} ifaces=${device.interfaceCount}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
    Spacer(Modifier.height(5.dp))
    Text(
        "No USB serial numbers are read. Mounted mass storage may not appear here.",
        style = MaterialTheme.typography.labelSmall,
        color = Faint,
    )
}

@Composable
private fun AudioPanel(audio: AudioInventory) {
    val audioError = audio.error
    if (audioError != null) {
        Text(audioError, style = MaterialTheme.typography.bodySmall, color = Red)
    }
    (audio.outputDevices + audio.inputDevices).take(12).forEach { device ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(device.name, style = MaterialTheme.typography.bodySmall, color = Paper)
            Text(
                "type=${device.type} sink=${device.sink} source=${device.source} " +
                    "channels=${device.channelCounts} rates=${device.sampleRates}",
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
    if (audio.outputDevices.isEmpty() && audio.inputDevices.isEmpty()) {
        Text("No audio devices reported.", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}
