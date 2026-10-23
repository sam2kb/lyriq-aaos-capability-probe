package com.sam2kb.lyriqprobe.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.AndroidSensorInfo
import com.sam2kb.lyriqprobe.core.util.formatFloat
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun SensorsScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val sensors = report?.androidSensors
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning

    ScreenScroll {
        PageHeader(
            kicker = "SensorManager",
            title = "Android sensors",
            description = "The sensors exposed directly to this AAOS app. This is not the LYRIQ VHAL and not the " +
                "sensor list a phone running Android Auto would report.",
            aside = {
                if (sensors != null) {
                    SmallTag("${sensors.sensors.size} SENSORS", Teal)
                }
            },
        )

        if (sensors == null) {
            EmptyNotice("Run Quick Scan to inventory Android sensors.")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Run Quick Scan",
                viewModel::runQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning,
            )
            return@ScreenScroll
        }

        Notice(
            NoticeTone.INFO,
            "Inventory reads sensor metadata. Live sampling is a separate 10-second test, one sensor at a time, " +
                "with listener cleanup in a finally block.",
        )
        Spacer(Modifier.height(14.dp))

        sensors.lastSample?.let { sample ->
            Panel(title = "Latest sample", kicker = "Active evidence", accent = Teal) {
                KeyValue("Sensor", sample.sensorName)
                KeyValue("Type", sample.sensorType.toString())
                KeyValue("Events", sample.eventCount.toString())
                KeyValue("Duration", "${sample.durationMs} ms")
                KeyValue("Min", sample.minValues.joinToString { formatFloat(it) })
                KeyValue("Max", sample.maxValues.joinToString { formatFloat(it) })
                KeyValue("Mean", sample.meanValues.joinToString { formatFloat(it) })
                KeyValue("Last", sample.lastValues.joinToString { formatFloat(it) })
                Spacer(Modifier.height(6.dp))
                Text(sample.summary, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.height(14.dp))
        }

        Panel(title = "Inventory", kicker = "TYPE_ALL", accent = Muted) {
            sensors.sensors.forEach { sensor ->
                SensorRow(
                    sensor = sensor,
                    canAct = canAct,
                    onSample = { viewModel.sampleSensor(sensor.type, sensor.name) },
                )
            }
            if (sensors.sensors.isEmpty()) {
                Text("No Android sensors exposed.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(sensors.note, style = MaterialTheme.typography.labelSmall, color = Faint)
        }
        Spacer(Modifier.height(14.dp))

        val sampleResults = report?.testResults.orEmpty().filter {
            it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.ACTIVE &&
                it.module == com.sam2kb.lyriqprobe.core.model.ProbeModule.SENSORS
        }
        if (sampleResults.isNotEmpty()) {
            Panel(title = "Sampling log", kicker = "Active evidence", accent = Teal) {
                sampleResults.reversed().take(10).forEach { result ->
                    ResultRow(result.name, result.status, result.summary)
                }
            }
        }
    }
}

@Composable
private fun SensorRow(
    sensor: AndroidSensorInfo,
    canAct: Boolean,
    onSample: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sensor.name, style = MaterialTheme.typography.bodyMedium, color = Paper)
                    Spacer(Modifier.width(7.dp))
                    if (sensor.wakeUpSensor) SmallTag("WAKE-UP", Amber)
                }
                Text(
                    "${sensor.stringType} / ${sensor.type} · ${sensor.vendor}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "range=${sensor.maximumRange} res=${sensor.resolution} power=${sensor.powerMilliAmp}mA " +
                        "mode=${sensor.reportingMode} minDelay=${sensor.minDelayMicros}us fifo=${sensor.fifoMaxEventCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            ActionButton(
                label = "Sample 10s",
                onClick = onSample,
                enabled = canAct,
                modifier = Modifier.width(110.dp),
            )
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}
