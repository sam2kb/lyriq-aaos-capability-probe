package com.sam2kb.lyriqprobe.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
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
import com.sam2kb.lyriqprobe.core.model.AppExternalDirInfo
import com.sam2kb.lyriqprobe.core.model.StorageStep
import com.sam2kb.lyriqprobe.core.model.StorageVolumeInfo
import com.sam2kb.lyriqprobe.core.util.formatBytes
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.Teal

private class OpenPersistableTree : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra("android.provider.extra.INITIAL_URI", input)
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK) return null
        return intent?.data
    }
}

@Composable
fun StorageScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val storage = report?.storage
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning
    val treePicker = rememberLauncherForActivityResult(OpenPersistableTree()) { uri ->
        if (uri != null) viewModel.onTreeSelected(uri)
    }

    ScreenScroll {
        PageHeader(
            kicker = "Storage / SAF",
            title = "Storage access",
            description = "Enumerate volumes and prove write access only inside a folder you choose. The probe " +
                "creates one uniquely named temporary file, verifies it, and deletes only that file.",
            aside = {
                if (storage != null) {
                    SmallTag("${storage.volumes.size} VOLUMES", Teal)
                }
            },
        )

        if (storage == null) {
            EmptyNotice("Run Quick Scan to enumerate volumes and app-specific external storage.")
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
            "Mounted USB mass storage may appear as a StorageVolume while being absent from UsbManager. " +
                "Android also restricts selecting some SD-card roots. Choose the deepest folder the system allows.",
        )
        Spacer(Modifier.height(14.dp))

        Panel(title = "Volumes", kicker = "StorageManager", accent = Teal) {
            storage.volumes.forEach { VolumeRow(it) }
            if (storage.volumes.isEmpty()) {
                Text("No StorageManager volumes exposed.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "External state: ${storage.externalStorageState}",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "App-specific external directories", kicker = "No broad storage permission", accent = Teal) {
            if (storage.appExternalDirs.isEmpty()) {
                Text("No app-specific external directories are available.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            storage.appExternalDirs.forEach { DirRow(it) }
            Spacer(Modifier.height(6.dp))
            Text(
                "These directories belong to the app, not the removable volume root. They do not prove access to " +
                    "the rear SD-card root or arbitrary USB contents.",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Selected folder", kicker = "Storage Access Framework", accent = Amber) {
            val tree = storage.selectedTree
            if (tree == null) {
                Text(
                    "No folder selected. Opening the system picker requires an interactive parked session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            } else {
                KeyValue("Display name", tree.displayName)
                KeyValue("Readable", tree.canRead.toString())
                KeyValue("Writable", tree.canWrite.toString())
                KeyValue("Persisted read", tree.readPersisted.toString())
                KeyValue("Persisted write", tree.writePersisted.toString())
                KeyValue("Children", tree.childCount?.toString() ?: "not enumerated")
                val treeError = tree.error
                if (treeError != null) {
                    Spacer(Modifier.height(6.dp))
                    Notice(NoticeTone.DANGER, treeError)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = if (tree == null) "Choose folder" else "Change folder",
                    onClick = { treePicker.launch(null) },
                    enabled = canAct,
                )
                ActionButton(
                    label = if (state.activeBusy != null) "Testing" else "Test storage",
                    onClick = viewModel::testStorage,
                    enabled = canAct && tree?.canWrite == true,
                    primary = true,
                )
                if (tree != null) {
                    ActionButton(
                        label = "Forget access",
                        onClick = viewModel::clearSelectedTree,
                        enabled = canAct,
                        danger = true,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Test sequence: resolve tree → create unique file → write known bytes → flush/close → read back " +
                    "and verify SHA-256 → delete only that file.",
                style = MaterialTheme.typography.labelSmall,
                color = Faint,
            )
        }
        Spacer(Modifier.height(14.dp))

        storage.testRecord?.let { record ->
            Panel(
                title = if (record.success) "Write/read/delete passed" else "Write/read/delete failed",
                kicker = "SAF test evidence",
                accent = if (record.success) Teal else Red,
            ) {
                KeyValue("Folder", record.displayName)
                KeyValue("Started", record.startedAt)
                KeyValue("Completed", record.completedAt)
                if (record.exceptionMessage != null) {
                    KeyValue("Exception", "${record.exceptionClass}: ${record.exceptionMessage}", valueColor = Red)
                }
                Spacer(Modifier.height(8.dp))
                record.steps.forEach { step -> StepRow(step) }
            }
            Spacer(Modifier.height(14.dp))
        }

        Text(
            storage.storageAccessFrameworkNote,
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}

@Composable
private fun VolumeRow(volume: StorageVolumeInfo) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(volume.description, style = MaterialTheme.typography.bodyMedium, color = Paper)
                Text(
                    "state=${volume.state} · removable=${volume.removable} · emulated=${volume.emulated} · primary=${volume.primary}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
                Text(
                    volume.path ?: "path unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatBytes(volume.availableBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = Teal,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "free of ${formatBytes(volume.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }
        }
        val accessError = volume.accessError
        if (accessError != null) {
            Text(accessError, style = MaterialTheme.typography.labelSmall, color = Red)
        }
    }
    HorizontalDivider(color = Line.copy(alpha = 0.55f))
}

@Composable
private fun DirRow(dir: AppExternalDirInfo) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(dir.label, style = MaterialTheme.typography.bodySmall, color = Paper)
        Text(
            "${dir.path} · read=${dir.canRead} write=${dir.canWrite} removable=${dir.removable} " +
                "free=${formatBytes(dir.availableBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun StepRow(step: StorageStep) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            if (step.success) "PASS" else "FAIL",
            style = MaterialTheme.typography.labelSmall,
            color = if (step.success) Teal else Red,
            modifier = Modifier.width(48.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(step.name, style = MaterialTheme.typography.bodySmall, color = Paper)
            Text(step.detail, style = MaterialTheme.typography.labelSmall, color = Faint)
        }
    }
}
