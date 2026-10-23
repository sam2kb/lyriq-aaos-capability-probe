package com.sam2kb.lyriqprobe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.PermissionEntry
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Green
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun PermissionsScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val permissions = state.report?.permissions
    var pendingPermission by remember { mutableStateOf<String?>(null) }
    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permission = pendingPermission
        pendingPermission = null
        if (permission != null) {
            viewModel.onPermissionResult(permission, granted)
        }
    }

    ScreenScroll {
        PageHeader(
            kicker = "Access matrix",
            title = "Permissions",
            description = "Declared, granted, requestable, and protected permissions. Signature and privileged " +
                "permissions are reported as boundaries, never bypassed.",
            aside = {
                if (permissions != null) {
                    SmallTag("${permissions.entries.count { entry -> entry.granted }} GRANTED", Teal)
                }
            },
        )

        if (permissions == null) {
            EmptyNotice("Run Quick Scan to build the permission matrix.")
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
            "Only runtime-dangerous permissions declared by this build can be requested. A denial is recorded " +
                "as evidence. Normal permissions are granted at install; signature/privileged permissions are not obtainable.",
        )
        Spacer(Modifier.height(14.dp))

        val requestable = permissions.entries.filter { it.requestable && !it.granted }
        if (requestable.isNotEmpty()) {
            SectionTitle("Requestable now", "${requestable.size} DENIED OR NOT YET REQUESTED", Teal)
            Panel {
                requestable.forEach { entry ->
                    PermissionRow(
                        entry = entry,
                        action = {
                            ActionButton(
                                label = if (pendingPermission == entry.name) "WAITING" else "Request",
                                onClick = {
                                    pendingPermission = entry.name
                                    requestLauncher.launch(entry.name)
                                },
                                enabled = state.parkedPermitted &&
                                    pendingPermission == null &&
                                    state.activeBusy == null,
                                modifier = Modifier.width(104.dp),
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        val automotive = permissions.entries.filter { it.name.startsWith("android.car.permission.") }
        SectionTitle("Automotive read permissions", "ANDROID.CAR", Amber)
        Panel {
            automotive.forEach { PermissionRow(it) }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Privileged / signature-only", "${permissions.protectedPermissions.size} BOUNDARIES", Red)
        Panel {
            val protected = permissions.entries.filter {
                it.existsOnDevice && (it.protectionLevel == "SIGNATURE" || "PRIVILEGED" in it.protectionFlags)
            }
            if (protected.isEmpty()) {
                Text("No catalogued protected permission is present.", style = MaterialTheme.typography.bodySmall, color = Muted)
            } else {
                protected.forEach { entry ->
                    PermissionRow(entry)
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        SectionTitle("Full catalogue", "CURATED — NOT EXHAUSTIVE", Muted)
        Panel {
            permissions.entries.forEach { entry ->
                PermissionRow(entry)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                permissions.catalogNote,
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        if (permissions.declaredButUnavailable.isNotEmpty()) {
            Notice(
                NoticeTone.WARN,
                "Declared but not defined by this platform: ${permissions.declaredButUnavailable.joinToString()}. " +
                    "Those automotive permissions may be OEM-gated or renamed.",
            )
            Spacer(Modifier.height(14.dp))
        }

        Text(
            "No SYSTEM_CAMERA, MANAGE_EXTERNAL_STORAGE, RECORD_AUDIO, or vehicle-write permission is requested by this build. " +
                "The catalogue only reports whether those permissions exist.",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun SectionTitle(title: String, meta: String, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Paper)
        Text(meta, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

@Composable
private fun PermissionRow(
    entry: PermissionEntry,
    action: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium, color = Paper)
                    Spacer(Modifier.width(7.dp))
                    SmallTag(entry.grantState, grantColor(entry.grantState))
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(3.dp))
                Text(entry.purpose, style = MaterialTheme.typography.bodySmall, color = Muted)
                val detail = buildString {
                    append("protection=")
                    append(entry.protectionLevel)
                    if (entry.protectionFlags.isNotEmpty()) {
                        append("|")
                        append(entry.protectionFlags.joinToString("|"))
                    }
                    append(" · declared=")
                    append(entry.declared)
                    append(" · exists=")
                    append(entry.existsOnDevice)
                }
                Spacer(Modifier.height(3.dp))
                Text(detail, style = MaterialTheme.typography.labelSmall, color = Faint)
                val note = entry.note
                if (!note.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(note, style = MaterialTheme.typography.labelSmall, color = grantColor(entry.grantState))
                }
            }
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                action()
            }
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.65f))
}

private fun grantColor(state: String): Color = when {
    state == "GRANTED" -> Green
    state == "DECLARED_NOT_GRANTED" -> Amber
    state == "PRIVILEGED_ONLY" -> Red
    state == "NOT_ON_DEVICE" -> Faint
    else -> Muted
}
