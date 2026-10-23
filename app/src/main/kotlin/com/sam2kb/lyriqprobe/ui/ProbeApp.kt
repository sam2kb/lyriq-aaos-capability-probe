package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Ink
import com.sam2kb.lyriqprobe.ui.theme.InkRaised
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.LineBright
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Slate
import com.sam2kb.lyriqprobe.ui.theme.Teal

enum class Page(
    val code: String,
    val title: String,
    val railLabel: String,
) {
    DASHBOARD("00", "Overview", "Overview"),
    PERMISSIONS("01", "Permissions", "Permissions"),
    CAMERAS("02", "Cameras", "Cameras"),
    VEHICLE("03", "Vehicle data", "Vehicle"),
    SENSORS("04", "Android sensors", "Sensors"),
    STORAGE("05", "Storage", "Storage"),
    NETWORK("06", "Network & VPN", "Network"),
    REPORTS("07", "Reports", "Reports"),
}

@Composable
fun ProbeApp(viewModel: ProbeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pageCode by rememberSaveable { mutableStateOf(Page.DASHBOARD.name) }
    val page = runCatching { Page.valueOf(pageCode) }.getOrDefault(Page.DASHBOARD)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Ink, Color(0xFF0A1116), Ink),
                ),
            )
            .safeDrawingPadding(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 56.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Line.copy(alpha = 0.075f),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Line.copy(alpha = 0.075f),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += step
            }
        }
        val wide = maxWidth >= 900.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                NavigationRail(
                    state = state,
                    current = page,
                    onSelect = { pageCode = it.name },
                    onQuickScan = { viewModel.runQuickScan() },
                )
            }
            Column(Modifier.fillMaxHeight().weight(1f)) {
                GlobalBar(
                    state = state,
                    page = page,
                    wide = wide,
                    onQuickScan = { viewModel.runQuickScan() },
                )
                if (!wide) {
                    CompactNav(
                        current = page,
                        onSelect = { pageCode = it.name },
                    )
                }
                if (state.message != null) {
                    MessageStrip(
                        text = state.message!!,
                        onDismiss = { viewModel.dismissMessage() },
                    )
                }
                if (state.activeBusy != null) {
                    ActiveStrip(state.activeBusy!!)
                }
                when (page) {
                    Page.DASHBOARD -> DashboardScreen(
                        state = state,
                        onQuickScan = { viewModel.runQuickScan() },
                        onAttest = viewModel::setParkedAttested,
                        onNavigate = { pageCode = it.name },
                    )
                    Page.PERMISSIONS -> PermissionsScreen(state, viewModel)
                    Page.CAMERAS -> CamerasScreen(state, viewModel)
                    Page.VEHICLE -> VehicleScreen(state, viewModel)
                    Page.SENSORS -> SensorsScreen(state, viewModel)
                    Page.STORAGE -> StorageScreen(state, viewModel)
                    Page.NETWORK -> NetworkScreen(state, viewModel)
                    Page.REPORTS -> ReportsScreen(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(
    state: ProbeUiState,
    current: Page,
    onSelect: (Page) -> Unit,
    onQuickScan: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(InkRaised)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(22.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Page.entries.forEach { item ->
                RailItem(
                    page = item,
                    selected = current == item,
                    onClick = { onSelect(item) },
                    state = state,
                )
                Spacer(Modifier.height(3.dp))
            }
        }
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(14.dp))
        Text(
            "QUICK SCAN",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
        Spacer(Modifier.height(7.dp))
        ActionButton(
            label = if (state.scanRunning) "SCANNING" else "RUN DISCOVERY",
            onClick = onQuickScan,
            primary = true,
            enabled = state.parkedPermitted && !state.scanRunning && state.activeBusy == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "READ-ONLY\nNO VIN / NO MEDIA",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
            fontFamily = FontFamily.Monospace,
            lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            state.app?.let { "v${it.versionName}" } ?: "v—",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

@Composable
private fun RailItem(
    page: Page,
    selected: Boolean,
    onClick: () -> Unit,
    state: ProbeUiState,
) {
    val accent = when (page) {
        Page.NETWORK -> Teal
        Page.REPORTS -> Paper
        else -> Amber
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            page.code,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else Faint,
            modifier = Modifier.width(27.dp),
        )
        Text(
            page.railLabel.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Paper else Muted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (itemNeedsAttention(page, state)) {
            Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(Amber))
        }
    }
}

private fun itemNeedsAttention(page: Page, state: ProbeUiState): Boolean {
    val report = state.report ?: return false
    return when (page) {
        Page.CAMERAS -> (report.cameras?.cameraCount ?: 0) > 0
        Page.VEHICLE -> (report.vehicleProperties?.propertyCount ?: 0) > 0
        Page.NETWORK -> report.vpn?.authorizationState == "CONSENT_REQUIRED"
        else -> false
    }
}

@Composable
private fun CompactNav(current: Page, onSelect: (Page) -> Unit) {
    Surface(color = InkRaised, border = androidx.compose.foundation.BorderStroke(0.dp, Color.Transparent)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(Page.entries, key = { it.name }) { item ->
                val selected = current == item
                Surface(
                    color = if (selected) Amber.copy(alpha = 0.13f) else Color.Transparent,
                    contentColor = if (selected) Amber else Muted,
                    shape = RoundedCornerShape(2.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) Amber.copy(alpha = 0.6f) else Line,
                    ),
                    onClick = { onSelect(item) },
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.code, style = MaterialTheme.typography.labelSmall, color = Faint)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            item.railLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalBar(
    state: ProbeUiState,
    page: Page,
    wide: Boolean,
    onQuickScan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkRaised.copy(alpha = 0.72f))
            .padding(horizontal = if (wide) 24.dp else 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!wide) {
            Text(
                "LYRIQ PROBE",
                style = MaterialTheme.typography.labelLarge,
                color = Amber,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            "SESSION / ${page.code}",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
        Spacer(Modifier.weight(1f))
        if (state.restricted) {
            SmallTag("DRIVING LOCK", Red)
        } else if (state.needsAttestation && !state.parkedAttested) {
            SmallTag("PARKED?", Amber)
        } else if (state.automotiveFeature) {
            SmallTag("PARKED", Teal)
        } else {
            SmallTag("NON-AUTOMOTIVE", Muted)
        }
        if (!wide) {
            Spacer(Modifier.width(8.dp))
            ActionButton(
                label = "SCAN",
                onClick = onQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning && state.activeBusy == null,
            )
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun MessageStrip(text: String, onDismiss: () -> Unit) {
    val tone = when {
        text.contains("failed", true) || text.contains("error", true) -> NoticeTone.DANGER
        text.contains("driving", true) || text.contains("stopped", true) -> NoticeTone.WARN
        text.contains("complete", true) || text.contains("success", true) || text.contains("saved", true) -> NoticeTone.SUCCESS
        else -> NoticeTone.INFO
    }
    val style = noticeStyle(tone)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(style.dim)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(style.label, style = MaterialTheme.typography.labelSmall, color = style.color)
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Paper,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "DISMISS",
            style = MaterialTheme.typography.labelSmall,
            color = style.color,
            modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
        )
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun ActiveStrip(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Amber.copy(alpha = 0.07f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Amber)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Paper)
        Spacer(Modifier.width(12.dp))
        ThinProgress(value = null, modifier = Modifier.width(140.dp))
    }
    HorizontalDivider(color = Line)
}
