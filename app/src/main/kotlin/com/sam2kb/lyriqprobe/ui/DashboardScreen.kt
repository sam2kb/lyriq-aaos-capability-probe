package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ScanPhase
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Blue
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.InkRaised
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun DashboardScreen(
    state: ProbeUiState,
    onQuickScan: () -> Unit,
    onAttest: (Boolean) -> Unit,
    onNavigate: (Page) -> Unit,
) {
    val report = state.report
    ScreenScroll {
        PageHeader(
            kicker = "Mission control",
            title = "Vehicle capability overview",
            description = "Read-only evidence first. Camera visibility is not capture access, and an accessible " +
                "property is not proof that its value can be read.",
            aside = {
                ActionButton(
                    label = if (state.scanRunning) "Scanning" else "Quick Scan",
                    onClick = onQuickScan,
                    primary = true,
                    enabled = state.parkedPermitted && !state.scanRunning && state.activeBusy == null,
                )
            },
        )

        if (state.restricted) {
            Notice(
                NoticeTone.DANGER,
                "The vehicle reports a restricted driving state. Interactive diagnostics are locked until AAOS " +
                    "reports an unrestricted, parked state.",
            )
            Spacer(Modifier.height(14.dp))
        }
        if (state.needsAttestation && !state.parkedAttested) {
            ParkedAttestationCard(onAttest)
            Spacer(Modifier.height(14.dp))
        }
        if (state.scanRunning) {
            ScanProgress(state)
            Spacer(Modifier.height(14.dp))
        }
        if (state.scanPhase == ScanPhase.FAILED) {
            Notice(NoticeTone.DANGER, state.message ?: "Quick Scan failed.")
            Spacer(Modifier.height(14.dp))
        }

        CameraVerdict(report, onNavigate)
        Spacer(Modifier.height(14.dp))

        MetricsRow(report)
        Spacer(Modifier.height(14.dp))

        ModuleGrid(report, onNavigate)
        Spacer(Modifier.height(14.dp))

        if (report != null) {
            Panel(title = "Key findings", kicker = "Read-out", accent = Amber) {
                if (report.summary.keyFindings.isEmpty()) {
                    Text("No findings yet.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                } else {
                    report.summary.keyFindings.forEach { finding ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(4.dp)
                                    .background(if (finding.contains("no cameras", true)) Red else Teal),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                finding,
                                style = MaterialTheme.typography.bodySmall,
                                color = Paper,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            RecentEvidence(report)
        } else {
            Panel(title = "No scan yet", kicker = "Session", accent = Amber) {
                Text(
                    "Run Quick Scan to inventory permissions, Camera2 IDs, vehicle properties, Android sensors, " +
                        "storage, connectivity, media readiness, and LMS network policy. No camera is opened, " +
                        "no value is written, and no LAN socket is opened.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    "Run first Quick Scan",
                    onQuickScan,
                    primary = true,
                    enabled = state.parkedPermitted && !state.scanRunning,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "No VIN, precise location, account data, or device serial number is collected. " +
                "Reports stay on the vehicle unless you export them.",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun ParkedAttestationCard(onAttest: (Boolean) -> Unit) {
    Panel(title = "Parked confirmation required", kicker = "Safety gate", accent = Amber) {
        Text(
            "The platform did not expose a usable CarUxRestrictions state. Active camera, sensor, storage, " +
                "vehicle-value, media foreground-service, and LMS network tests are disabled until you confirm " +
                "the vehicle is parked. " +
                "This confirmation cannot override a restricted state if the platform later reports one.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = false,
                onCheckedChange = { onAttest(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = Amber,
                    uncheckedColor = Line,
                    checkmarkColor = InkRaised,
                ),
            )
            Text(
                "I confirm the vehicle is parked and it is safe to run interactive diagnostics.",
                style = MaterialTheme.typography.bodySmall,
                color = Paper,
            )
        }
    }
}

@Composable
private fun ScanProgress(state: ProbeUiState) {
    Panel(kicker = "Working", accent = Teal) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.scanStep,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
            )
            Text(
                "${state.scanCompleted}/${state.scanTotal}",
                style = MaterialTheme.typography.labelMedium,
                color = Teal,
            )
        }
        Spacer(Modifier.height(9.dp))
        ThinProgress(
            value = if (state.scanTotal == 0) null else state.scanCompleted.toFloat() / state.scanTotal,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Discovery only. No camera is opened, no file is written, and no vehicle value is read by Quick Scan.",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun CameraVerdict(report: ProbeReport?, onNavigate: (Page) -> Unit) {
    val results = report?.testResults.orEmpty()
    val opened = results.any { it.id.startsWith("camera.open.") && it.status == ResultStatus.SUCCESS }
    val captured = results.any { it.id.startsWith("camera.frame.") && it.status == ResultStatus.SUCCESS }
    val cameras = report?.cameras
    val cameraCount = cameras?.cameraCount ?: 0
    val verdict = when {
        captured -> "FRAME CAPTURE CONFIRMED"
        opened -> "OPEN/CLOSE CONFIRMED"
        report == null -> "AWAITING SCAN"
        cameras == null -> "CAMERA INVENTORY UNAVAILABLE"
        cameraCount == 0 -> "NO CAMERAS EXPOSED"
        else -> "CAMERAS VISIBLE — ACCESS UNPROVEN"
    }
    val verdictColor = when {
        captured -> Teal
        opened -> Teal
        report == null -> Muted
        cameraCount == 0 -> Amber
        else -> Amber
    }
    val detail = when {
        report == null -> "Run Quick Scan to query CameraManager.getCameraIdList()."
        cameras == null -> "The camera inventory did not complete. See the Cameras tab or the JSON error entry."
        cameraCount == 0 -> "The ordinary app sees no Camera2 IDs. Factory cameras may be installed but system-only; " +
            "this is not a statement that the LYRIQ has no cameras."
        captured -> "A single in-memory frame was received and discarded. This is the strongest camera evidence v1 collects."
        opened -> "A CameraDevice opened and closed. Frame capture has not yet been proven."
        else -> "Camera IDs are visible, but no camera has been opened. Enumeration is not capture access."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF101B21), Color(0xFF0D151A)),
                ),
            )
            .padding(1.dp)
            .background(InkRaised)
            .padding(22.dp),
    ) {
        SectionKicker("Primary question", Amber)
        Spacer(Modifier.height(12.dp))
        Text(
            verdict,
            style = MaterialTheme.typography.displayMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = verdictColor,
        )
        Spacer(Modifier.height(10.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetricInline("${report?.cameras?.cameraCount ?: "—"}", "exposed IDs")
            MetricInline("${if (opened) "YES" else "—"}", "open/close")
            MetricInline("${if (captured) "YES" else "—"}", "single frame")
            Spacer(Modifier.weight(1f))
            ActionButton("Inspect cameras", { onNavigate(Page.CAMERAS) })
        }
    }
}

@Composable
private fun MetricInline(value: String, label: String) {
    Column(Modifier.padding(end = 26.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            color = Paper,
        )
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Faint)
    }
}

@Composable
private fun MetricsRow(report: ProbeReport?) {
    val permissions = report?.permissions
    val vehicle = report?.vehicleProperties
    val sensors = report?.androidSensors
    val storage = report?.storage
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricTile(
            value = if (permissions == null) "—" else "${permissions.entries.count { it.granted }}/${permissions.entries.size}",
            label = "permissions granted",
            accent = Teal,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            value = vehicle?.propertyCount?.toString() ?: "—",
            label = "vehicle properties",
            accent = Amber,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            value = sensors?.sensors?.size?.toString() ?: "—",
            label = "Android sensors",
            accent = Paper,
            modifier = Modifier.weight(1f),
        )
        MetricTile(
            value = if (storage == null) "—" else "${storage.volumes.size}",
            label = "storage volumes",
            accent = Paper,
            modifier = Modifier.weight(1f),
        )
    }
}

private data class ModuleSummary(
    val page: Page,
    val kicker: String,
    val value: String,
    val detail: String,
    val accent: Color,
)

@Composable
private fun ModuleGrid(report: ProbeReport?, onNavigate: (Page) -> Unit) {
    val permissions = report?.permissions
    val vehicle = report?.vehicleProperties
    val sensors = report?.androidSensors
    val storage = report?.storage
    val media = report?.media
    val lms = report?.lmsNetwork
    val modules = listOf(
        ModuleSummary(
            Page.PERMISSIONS,
            "Permissions",
            if (permissions == null) "—" else "${permissions.entries.count { it.granted }}/${permissions.entries.size}",
            if (permissions == null) "Not scanned" else
                "${permissions.protectedPermissions.size} protected · ${permissions.runtimeRequestable.size} requestable now",
            Teal,
        ),
        ModuleSummary(
            Page.VEHICLE,
            "Vehicle data",
            vehicle?.propertyCount?.toString() ?: "—",
            when {
                vehicle == null -> "Not scanned"
                vehicle.propertyListError != null -> "Property list error"
                else -> "${vehicle.readings.count { it.status == ResultStatus.SUCCESS }} values read on demand"
            },
            Amber,
        ),
        ModuleSummary(
            Page.SENSORS,
            "Sensors",
            sensors?.sensors?.size?.toString() ?: "—",
            sensors?.lastSample?.let { "${it.eventCount} events in last sample" } ?: "Inventory only; 10s sample on demand",
            Paper,
        ),
        ModuleSummary(
            Page.STORAGE,
            "Storage",
            storage?.volumes?.size?.toString() ?: "—",
            when {
                storage == null -> "Not scanned"
                storage.testRecord?.success == true -> "SAF test passed"
                storage.testRecord?.success == false -> "SAF test failed"
                else -> "Write test not run"
            },
            Teal,
        ),
        ModuleSummary(
            Page.MEDIA,
            "Media readiness",
            media?.let { "${it.mimeSupport.count { entry -> entry.value }}/${it.mimeSupport.size}" } ?: "—",
            when {
                media == null -> "Not scanned"
                media.lastForegroundServiceTest?.started == true -> "mediaPlayback FGS started"
                media.lastSilentAudioTest?.succeeded == true -> "AudioTrack path verified"
                else -> "${media.audioDecoders.size} decoders · FGS test not run"
            },
            Blue,
        ),
        ModuleSummary(
            Page.NETWORK,
            "LMS network",
            lms?.lastTest?.takeIf { it.isNotEmpty() }?.let { "${it.count { test -> test.connected }}/${it.size}" } ?: "—",
            when {
                lms == null -> "Not scanned"
                lms.lastTest.any { it.connected } -> "LMS HTTP/Slimproto reachable"
                lms.lastTest.isNotEmpty() -> "Last endpoint test failed"
                else -> "No endpoint test yet"
            },
            Teal,
        ),
        ModuleSummary(
            Page.REPORTS,
            "Report",
            if (report == null) "—" else "${report.testResults.size}",
            if (report == null) "No session" else "${report.testResults.size} tests · JSON ready",
            Paper,
        ),
    )

    BoxWithConstraints {
        val singleColumn = maxWidth < 620.dp
        if (singleColumn) {
            modules.forEach { module ->
                ModuleCard(module) { onNavigate(module.page) }
                Spacer(Modifier.height(10.dp))
            }
        } else {
            modules.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { module ->
                        ModuleCard(module, Modifier.weight(1f)) { onNavigate(module.page) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: ModuleSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Panel(modifier = modifier, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                SectionKicker(module.kicker, module.accent)
                Spacer(Modifier.height(8.dp))
                Text(
                    module.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = Paper,
                )
            }
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge,
                color = module.accent,
                modifier = Modifier
                    .background(module.accent.copy(alpha = 0.10f), RoundedCornerShape(2.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(module.detail, style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@Composable
private fun RecentEvidence(report: ProbeReport) {
    val active = report.testResults.filter { it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.ACTIVE }.takeLast(4)
    if (active.isEmpty()) return
    Panel(title = "Latest active evidence", kicker = "Timeline", accent = Teal) {
        active.reversed().forEach { result ->
            ResultRow(
                title = result.name,
                status = result.status,
                summary = result.summary,
            )
        }
    }
}
