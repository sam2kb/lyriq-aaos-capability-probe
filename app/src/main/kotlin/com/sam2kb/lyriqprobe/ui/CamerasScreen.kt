package com.sam2kb.lyriqprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.sam2kb.lyriqprobe.ProbeUiState
import com.sam2kb.lyriqprobe.ProbeViewModel
import com.sam2kb.lyriqprobe.core.model.CameraDescriptor
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.SlateHigh
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun CamerasScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val report = state.report
    val cameras = report?.cameras
    var captureTarget by remember { mutableStateOf<CameraDescriptor?>(null) }
    val cameraPermission = report?.permissions?.entries
        ?.firstOrNull { it.name == "android.permission.CAMERA" }
    val canAct = state.parkedPermitted && cameraPermission?.granted == true &&
        state.activeBusy == null && !state.scanRunning

    if (captureTarget != null) {
        val target = captureTarget!!
        AlertDialog(
            onDismissRequest = { captureTarget = null },
            containerColor = SlateHigh,
            title = {
                Text(
                    "One in-memory frame from camera ${target.id}?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper,
                )
            },
            text = {
                Text(
                    "The probe will open the camera, configure one ImageReader, request exactly one frame, " +
                        "close the session and camera, and discard the bytes. It will not write an image, record video, " +
                        "or run in the background. Do not use this while the vehicle is moving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    captureTarget = null
                    viewModel.captureFrame(target.id)
                }) {
                    Text("CAPTURE ONE FRAME", color = Amber, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { captureTarget = null }) {
                    Text("CANCEL", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    ScreenScroll {
        PageHeader(
            kicker = "Camera2 / highest priority",
            title = "Camera exposure",
            description = "Find out what the ordinary app can see, open, and receive. Enumeration, open/close, and a " +
                "single frame are separate evidence levels.",
            aside = {
                if (cameras != null) {
                    SmallTag("${cameras.cameraCount} EXPOSED", if (cameras.cameraCount == 0) Amber else Teal)
                }
            },
        )

        if (cameras == null) {
            EmptyNotice("Run Quick Scan to enumerate Camera2 devices.")
            Spacer(Modifier.height(12.dp))
            ActionButton(
                "Run Quick Scan",
                viewModel::runQuickScan,
                primary = true,
                enabled = state.parkedPermitted && !state.scanRunning,
            )
            return@ScreenScroll
        }

        if (cameras.cameraCount == 0) {
            Notice(
                NoticeTone.WARN,
                "No cameras exposed to this app. This is not evidence that the LYRIQ has no cameras. " +
                    "Factory cameras may be system-only and absent from Camera2 enumeration.",
            )
        } else {
            Notice(
                NoticeTone.INFO,
                "The app can see ${cameras.cameraCount} camera ID(s). Enumerating an ID does not prove that the device " +
                    "will open or that frames are obtainable. Run the explicit tests below while parked.",
            )
        }
        Spacer(Modifier.height(14.dp))

        if (cameraPermission?.granted != true) {
            Notice(
                NoticeTone.WARN,
                "android.permission.CAMERA is not granted. Enumeration and characteristics still ran; open and frame " +
                    "tests are disabled until you grant it from the Permissions tab.",
            )
            Spacer(Modifier.height(14.dp))
        }

        cameras.cameras.forEach { camera ->
            CameraPanel(
                camera = camera,
                state = state,
                canAct = canAct,
                onOpen = { viewModel.openCamera(camera.id) },
                onCapture = { captureTarget = camera },
            )
            Spacer(Modifier.height(12.dp))
        }

        Panel(title = "System-camera boundary", kicker = "Privileged path", accent = Red) {
            Text(
                cameras.systemCameraPermissionState,
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "SYSTEM_CAMERA is not declared or requested by this build. System-only cameras would not normally " +
                    "appear in getCameraIdList(). No hidden API, root, Binder transaction, or permission bypass is used.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Spacer(Modifier.height(12.dp))

        Panel(title = "Automotive EVS boundary", kicker = "Separate camera stack", accent = Amber) {
            Text(cameras.evsAssessment, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.height(12.dp))

        val active = report?.testResults.orEmpty().filter {
            it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.ACTIVE && it.module == ProbeModule.CAMERAS
        }
        if (active.isNotEmpty()) {
            Panel(title = "Camera test log", kicker = "Active evidence", accent = Teal) {
                active.reversed().forEach { result ->
                    ResultRow(
                        title = result.name,
                        status = result.status,
                        summary = result.summary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPanel(
    camera: CameraDescriptor,
    state: ProbeUiState,
    canAct: Boolean,
    onOpen: () -> Unit,
    onCapture: () -> Unit,
) {
    val report = state.report
    val openResult = report?.testResults?.lastOrNull { it.id == "camera.open.${camera.id}" }
    val frameResult = report?.testResults?.lastOrNull { it.id == "camera.frame.${camera.id}" }
    Panel(
        kicker = "Camera ${camera.id}",
        title = "${camera.facing} · ${camera.hardwareLevel}",
        accent = if (camera.inspectionError == null) Teal else Red,
    ) {
        val inspectionError = camera.inspectionError
        if (inspectionError != null) {
            Notice(NoticeTone.DANGER, inspectionError)
            return@Panel
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallTag(camera.facing, Teal)
            if (camera.logicalMultiCamera) SmallTag("LOGICAL", Amber)
            if (camera.physicalCameraIds.isNotEmpty()) {
                SmallTag("${camera.physicalCameraIds.size} PHYSICAL", Muted)
            }
            if (camera.automotiveLensFacing.isNotEmpty()) {
                SmallTag(camera.automotiveLensFacing.joinToString("/"), Amber)
            }
        }
        Spacer(Modifier.height(12.dp))
        KeyValue("Sensor orientation", camera.sensorOrientation?.toString() ?: "unknown")
        KeyValue("Active array", camera.activeArraySize ?: "unknown")
        KeyValue("Pixel array", camera.pixelArraySize ?: "unknown")
        KeyValue("Flash", camera.flashAvailable?.toString() ?: "unknown")
        KeyValue("Camera2 version", camera.infoVersion ?: "unknown")
        KeyValue("Physical IDs", camera.physicalCameraIds.ifEmpty { listOf("none") }.joinToString())
        KeyValue("Automotive location", camera.automotiveLocation ?: "not reported")
        if (camera.capabilities.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("CAPABILITIES", style = MaterialTheme.typography.labelSmall, color = Faint)
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                camera.capabilities.take(4).forEach { SmallTag(it, Muted) }
            }
        }
        if (camera.streamConfigurations.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("TOP OUTPUT STREAMS", style = MaterialTheme.typography.labelSmall, color = Faint)
            camera.streamConfigurations.take(4).forEach { stream ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stream.format, style = MaterialTheme.typography.labelSmall, color = Teal)
                    Text(
                        stream.sizes.take(4).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = Line)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionButton(
                label = if (state.activeBusy != null) "BUSY" else "Open + close",
                onClick = onOpen,
                enabled = canAct,
            )
            ActionButton(
                label = "Capture 1 frame",
                onClick = onCapture,
                enabled = canAct,
                primary = true,
            )
        }
        if (openResult != null || frameResult != null) {
            Spacer(Modifier.height(10.dp))
            openResult?.let {
                ResultRow("Open/close evidence", it.status, it.summary)
            }
            frameResult?.let {
                ResultRow("Single-frame evidence", it.status, it.summary)
            }
        }
    }
}
