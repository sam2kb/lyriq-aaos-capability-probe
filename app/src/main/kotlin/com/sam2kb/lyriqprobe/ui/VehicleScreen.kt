package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.background
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
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.VehiclePropertyInfo
import com.sam2kb.lyriqprobe.core.model.VehicleReading
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.SlateHigh
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun VehicleScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val vehicle = report?.vehicleProperties
    val automotive = report?.automotive
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning

    ScreenScroll {
        PageHeader(
            kicker = "Android Car / VHAL",
            title = "Vehicle data",
            description = "Inventory of properties the ordinary app is allowed to see. Values are read only on " +
                "explicit action. No write or actuator path exists in this build.",
            aside = {
                if (vehicle != null) {
                    SmallTag("${vehicle.propertyCount} PROPERTIES", Amber)
                }
            },
        )

        if (vehicle == null) {
            EmptyNotice("Run Quick Scan to connect to CarPropertyManager and inventory accessible properties.")
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
            "getPropertyList() returns only properties visible to this app. A property in the list may still be " +
                "unreadable. Cadillac/GM may withhold VHAL properties from third-party apps entirely.",
        )
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                value = vehicle.propertyCount.toString(),
                label = "visible properties",
                accent = Amber,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = vehicle.readings.count { it.status == ResultStatus.SUCCESS }.toString(),
                label = "values read",
                accent = Teal,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                value = if (automotive?.carConnected == true) "LIVE" else "NO",
                label = "car connection",
                accent = if (automotive?.carConnected == true) Teal else Red,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Service state", kicker = "android.car", accent = Teal) {
            KeyValue("Car connected", automotive?.carConnected?.toString() ?: "unknown")
            KeyValue("Car API", automotive?.carApiVersion ?: "unavailable")
            KeyValue("Platform", automotive?.carPlatformVersion ?: "unavailable")
            KeyValue("Property service", automotive?.propertyServiceAvailable?.toString() ?: "unknown")
            KeyValue("UX restrictions", automotive?.uxRestrictionServiceAvailable?.toString() ?: "unknown")
            KeyValue("Creation note", automotive?.creationError ?: "none")
            if (vehicle.propertyListError != null) {
                Spacer(Modifier.height(8.dp))
                Notice(NoticeTone.DANGER, "getPropertyList() failed: ${vehicle.propertyListError}")
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Read allowlisted values", kicker = "Active test", accent = Amber) {
            Text(
                "Reads make, model, year, trim, EV battery/charging information, range, speed, odometer, " +
                    "exterior temperature, and efficiency where each property is visible and readable. " +
                    "INFO_VIN is never read.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(Modifier.height(10.dp))
            ActionButton(
                label = if (state.activeBusy != null) "Busy" else "Read values now",
                onClick = viewModel::readVehicleValues,
                enabled = canAct && automotive?.carConnected == true,
                primary = true,
            )
            val latest = report?.testResults?.lastOrNull { it.id == "vehicle.read_values" }
            if (latest != null) {
                Spacer(Modifier.height(8.dp))
                ResultRow("Last read", latest.status, latest.summary)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (vehicle.readings.isNotEmpty()) {
            Panel(title = "Values", kicker = "Last successful read", accent = Teal) {
                vehicle.readings.forEach { reading ->
                    ReadingRow(reading)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        Panel(title = "Accessible property list", kicker = "Metadata only", accent = Muted) {
            vehicle.properties.take(120).forEach { property ->
                PropertyRow(property)
            }
            if (vehicle.properties.size > 120) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Showing 120 of ${vehicle.properties.size}. The complete list is in the JSON report.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }
            if (vehicle.properties.isEmpty()) {
                Text("No accessible properties were returned.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Privacy boundary", kicker = "Vehicle identity", accent = Red) {
            Text(vehicle.privacyNote, style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(8.dp))
            Text(vehicle.readPolicy, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Car App Library", kicker = "Not integrated", accent = Faint) {
            Text(
                report?.carAppLibrary?.reason ?: "Car App Library probe not run.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "This is a deliberate scope boundary, not a claim that the library is unsupported. A host-bound " +
                    "CarAppService is needed to observe CarInfo/CarSensors permissions and values.",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
    }
}

@Composable
private fun ReadingRow(reading: VehicleReading) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(reading.name, style = MaterialTheme.typography.bodyMedium, color = Paper)
                Text(
                    "area ${reading.areaId} · ${reading.valueType ?: "unknown"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                reading.value?.plus(reading.unit?.let { " $it" } ?: "") ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = if (reading.status == ResultStatus.SUCCESS) Teal else Muted,
            )
            Spacer(Modifier.width(10.dp))
            StatusChip(reading.status)
        }
        if (reading.status != ResultStatus.SUCCESS) {
            Spacer(Modifier.height(3.dp))
            Text(reading.summary, style = MaterialTheme.typography.labelSmall, color = Faint)
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun PropertyRow(property: VehiclePropertyInfo) {
    val isVin = property.name == "INFO_VIN"
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                property.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = if (isVin) Amber else Paper,
                modifier = Modifier.weight(1f),
            )
            Text(
                "0x${property.propertyId.toString(16)}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Faint,
            )
        }
        Text(
            "${property.type} · ${property.access} · ${property.changeMode} · areas=${property.areaIds}",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        if (isVin) {
            Text(
                "VIN metadata is visible; the value is never read or exported.",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
            )
        }
    }
}
