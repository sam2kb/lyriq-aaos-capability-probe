package com.sam2kb.lyriqprobe.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.AudioInventory
import com.sam2kb.lyriqprobe.core.model.NetworkInventory
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.UsbInventory
import com.sam2kb.lyriqprobe.core.model.VpnCapabilitySnapshot
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Green
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.SlateHigh
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun NetworkScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val connectivity = report?.connectivity
    val vpn = report?.vpn
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning
    var confirmVpn by remember { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        viewModel.onVpnConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    if (confirmVpn) {
        AlertDialog(
            onDismissRequest = { confirmVpn = false },
            containerColor = SlateHigh,
            title = { Text("Temporary VPN capability test?", style = MaterialTheme.typography.titleMedium, color = Paper) },
            text = {
                Text(
                    "This will ask Android/Cadillac for VPN consent, then create a TUN interface with one private " +
                        "address and NO routes, DNS servers, or application rules. It is closed immediately. " +
                        "No WireGuard handshake, key, traffic redirection, or remote tunnel is involved. " +
                        "No traffic should be routed through the interface. Do this only while parked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmVpn = false
                    val consentIntent = viewModel.startVpnTest()
                    if (consentIntent != null) consentLauncher.launch(consentIntent)
                }) {
                    Text("REQUEST CONSENT", color = Amber, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmVpn = false }) {
                    Text("CANCEL", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    ScreenScroll {
        PageHeader(
            kicker = "Connectivity / VPN",
            title = "Network & VPN capability",
            description = "Read-only connectivity inventory plus an explicit VpnService consent and temporary-TUN " +
                "test. The VPN test is a capability probe, not a VPN client.",
            aside = {
                if (vpn != null) {
                    SmallTag(vpn.authorizationState, vpnColor(vpn))
                }
            },
        )

        if (connectivity == null) {
            EmptyNotice("Run Quick Scan to inventory connectivity and VPN authorization state.")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Run Quick Scan",
                viewModel::runQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning,
            )
            return@ScreenScroll
        }

        Panel(title = "VPN capability", kicker = "VpnService.prepare → establish → close", accent = vpnColor(vpn)) {
            if (vpn == null) {
                Text(
                    "VPN probe has not run in this session. Quick Scan could not read a VPN snapshot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                Notice(
                    if (vpn.interfaceEstablished == true) NoticeTone.SUCCESS
                    else if (vpn.authorizationState.contains("DENIED", true)) NoticeTone.DANGER
                    else NoticeTone.INFO,
                    when {
                        vpn.interfaceEstablished == true && vpn.interfaceClosed == true ->
                            "A temporary TUN interface was established and closed. No routes, DNS servers, or allowed apps were configured."
                        vpn.authorizationState.contains("DENIED", true) ->
                            "VPN authorization was denied. No TUN interface was created."
                        vpn.authorizationState == "CONSENT_REQUIRED" ->
                            "Quick Scan called VpnService.prepare(); the OS says consent is required. No dialog was shown by Quick Scan."
                        vpn.interfaceEstablished == false ->
                            "The service started but establish() did not produce a TUN interface."
                        else ->
                            "VPN capability is ${vpn.authorizationState.lowercase().replace('_', ' ')}."
                    },
                )
                Spacer(Modifier.height(12.dp))
                KeyValue("API available", vpn.apiAvailable.toString())
                KeyValue("Service declared", vpn.serviceDeclared.toString())
                KeyValue("BIND_VPN_SERVICE", vpn.bindPermissionProtected.toString())
                KeyValue("Service exported", vpn.serviceExported?.toString() ?: "unknown")
                KeyValue("VPN intent filter", vpn.vpnIntentFilterPresent.toString())
                KeyValue("prepare() state", vpn.authorizationState)
                KeyValue("Consent dialog requested", vpn.consentDialogRequested.toString())
                KeyValue("Interface established", vpn.interfaceEstablished?.toString() ?: "not attempted")
                KeyValue("Interface closed", vpn.interfaceClosed?.toString() ?: "not attempted")
                KeyValue("Session name", vpn.sessionName ?: "—")
                KeyValue("Addresses", vpn.addressesAdded.ifEmpty { listOf("none") }.joinToString())
                KeyValue("Routes", vpn.routesAdded.ifEmpty { listOf("none") }.joinToString())
                KeyValue("DNS", vpn.dnsServers.ifEmpty { listOf("none") }.joinToString())
                KeyValue("Allowed apps", vpn.allowedApplications.ifEmpty { listOf("none") }.joinToString())
                KeyValue("Duration", vpn.durationMs?.let { "$it ms" } ?: "—")
                val vpnError = vpn.error
                if (vpnError != null) {
                    KeyValue("Error", vpnError, valueColor = Red)
                }
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    label = if (state.activeBusy != null) "VPN test busy" else "Test VPN consent + TUN",
                    onClick = { confirmVpn = true },
                    enabled = canAct,
                    primary = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "What this proves: whether AAOS shows the VPN consent dialog and whether an ordinary app can " +
                        "create and close a TUN interface. What it does not prove: WireGuard handshake, key storage, " +
                        "packet routing, throughput, or background survivability. No traffic is intentionally routed.",
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

private fun vpnColor(vpn: VpnCapabilitySnapshot?): androidx.compose.ui.graphics.Color = when {
    vpn?.interfaceEstablished == true && vpn.interfaceClosed == true -> Green
    vpn?.authorizationState?.contains("DENIED", true) == true -> Red
    vpn?.authorizationState == "CONSENT_REQUIRED" -> Amber
    else -> Teal
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
