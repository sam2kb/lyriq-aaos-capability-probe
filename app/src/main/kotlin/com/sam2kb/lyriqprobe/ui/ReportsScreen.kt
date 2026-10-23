package com.sam2kb.lyriqprobe.ui

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.util.formatBytes
import com.sam2kb.lyriqprobe.report.SavedReport
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun ReportsScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val context = LocalContext.current
    var pendingExport by remember { mutableStateOf<Pair<String, String>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val pending = pendingExport
        pendingExport = null
        if (uri != null && pending != null) {
            viewModel.writeExport(uri, pending.first)
        }
    }

    fun export(json: String?, name: String) {
        if (json == null) {
            Toast.makeText(context, "No JSON available.", Toast.LENGTH_SHORT).show()
            return
        }
        pendingExport = json to name
        exportLauncher.launch(name)
    }

    fun share(json: String?, name: String) {
        if (json == null) {
            Toast.makeText(context, "No JSON available.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = viewModel.createShareUri(json, name)
        if (uri == null) {
            Toast.makeText(context, "Could not create the share file.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share LYRIQ report"))
        }.onFailure {
            Toast.makeText(context, "No share target is available. Use Export JSON.", Toast.LENGTH_LONG).show()
        }
    }

    ScreenScroll {
        PageHeader(
            kicker = "Evidence / JSON",
            title = "Reports",
            description = "The complete machine-readable inventory and test log. Reports stay in app-private " +
                "storage until you export or share them.",
            aside = {
                if (report != null) {
                    SmallTag("SCHEMA ${report.schemaVersion}", Teal)
                }
            },
        )

        if (report == null) {
            EmptyNotice("Run Quick Scan to create the first report.")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Run Quick Scan",
                viewModel::runQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning,
            )
            return@ScreenScroll
        }

        Panel(kicker = "Current session", accent = Amber) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "LYRIQ capability report",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Paper,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        report.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = Faint,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "App ${report.appVersion} · schema ${report.schemaVersion} · ${report.testResults.size} tests",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                }
                SmallTag(
                    if (report.errors.isEmpty()) "NO ERRORS" else "${report.errors.size} ERRORS",
                    if (report.errors.isEmpty()) Teal else Red,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricTile(report.cameras?.cameraCount?.toString() ?: "—", "cameras", Teal, Modifier.weight(1f))
                MetricTile(report.vehicleProperties?.propertyCount?.toString() ?: "—", "vehicle props", Amber, Modifier.weight(1f))
                MetricTile(report.androidSensors?.sensors?.size?.toString() ?: "—", "sensors", Paper, Modifier.weight(1f))
                MetricTile(
                    report.vpn?.interfaceEstablished?.toString() ?: "—",
                    "VPN TUN",
                    if (report.vpn?.interfaceEstablished == true) Teal else Muted,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(
                    "Export JSON",
                    onClick = {
                        export(viewModel.currentJson(), "${viewModel.currentFileBaseName()}.json")
                    },
                    primary = true,
                    enabled = state.activeBusy == null && !state.scanRunning,
                )
                ActionButton(
                    "Share JSON",
                    onClick = {
                        share(viewModel.currentJson(), "${viewModel.currentFileBaseName()}.json")
                    },
                    enabled = state.activeBusy == null && !state.scanRunning,
                )
                ActionButton(
                    "Save locally",
                    onClick = viewModel::saveCurrentReport,
                    enabled = state.activeBusy == null && !state.scanRunning,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Status summary", kicker = "Test outcomes", accent = Teal) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                report.summary.statusCounts.filterValues { it > 0 }.forEach { (status, count) ->
                    val parsed = runCatching { ResultStatus.valueOf(status) }.getOrNull()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace,
                            color = parsed?.let(::statusColor) ?: Muted,
                        )
                        Text(status, style = MaterialTheme.typography.labelSmall, color = Faint)
                    }
                }
            }
            if (report.summary.statusCounts.values.all { it == 0 }) {
                Text("No tests recorded.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Key findings", kicker = "Human-readable", accent = Amber) {
            report.summary.keyFindings.forEach { finding ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("—", style = MaterialTheme.typography.bodySmall, color = Amber)
                    Spacer(Modifier.width(10.dp))
                    Text(finding, style = MaterialTheme.typography.bodySmall, color = Paper)
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(12.dp))
            SelectionText(report.summary.humanReadable)
        }
        Spacer(Modifier.height(14.dp))

        if (report.errors.isNotEmpty()) {
            Panel(title = "Errors", kicker = "Unexpected outcomes", accent = Red) {
                report.errors.forEach { error ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                        Text(
                            "${error.module} / ${error.testId}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Red,
                        )
                        Text(error.message, style = MaterialTheme.typography.bodySmall, color = Paper)
                        if (error.exceptionClass != null) {
                            Text(
                                "${error.exceptionClass}: ${error.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Faint,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Panel(title = "Saved reports", kicker = "App-private storage", accent = Muted) {
            if (state.savedReports.isEmpty()) {
                Text(
                    "No saved reports yet. Quick Scan saves one automatically; Save locally adds the current session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                state.savedReports.forEach { saved ->
                    SavedReportRow(
                        saved = saved,
                        onExport = {
                            export(viewModel.savedJson(saved), saved.displayName)
                        },
                        onShare = {
                            share(viewModel.savedJson(saved), saved.displayName)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "The JSON contains test_results, errors, permissions, cameras, vehicle_properties, android_sensors, " +
                "storage, connectivity, vpn, driving_state, and summary. It intentionally excludes VIN, precise " +
                "location, accounts, and device serial numbers.",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun SavedReportRow(
    saved: SavedReport,
    onExport: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                saved.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = Paper,
            )
            Text(
                "${formatBytes(saved.sizeBytes)} · ${saved.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.width(8.dp))
        ActionButton("Share", onShare)
        Spacer(Modifier.width(6.dp))
        ActionButton("Export", onExport, primary = true)
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun SelectionText(text: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = Muted,
        )
    }
}
