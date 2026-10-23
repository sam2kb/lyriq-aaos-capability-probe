package com.sam2kb.lyriqprobe.ui

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
import com.sam2kb.lyriqprobe.ui.theme.Amber
import com.sam2kb.lyriqprobe.ui.theme.Faint
import com.sam2kb.lyriqprobe.ui.theme.Line
import com.sam2kb.lyriqprobe.ui.theme.Muted
import com.sam2kb.lyriqprobe.ui.theme.Paper
import com.sam2kb.lyriqprobe.ui.theme.Red
import com.sam2kb.lyriqprobe.ui.theme.SlateHigh
import com.sam2kb.lyriqprobe.ui.theme.Teal

@Composable
fun MediaScreen(state: ProbeUiState, viewModel: ProbeViewModel) {
    val media = state.report?.media
    val canAct = state.parkedPermitted && state.activeBusy == null && !state.scanRunning
    var confirmFgs by remember { mutableStateOf(false) }

    if (confirmFgs) {
        AlertDialog(
            onDismissRequest = { confirmFgs = false },
            containerColor = SlateHigh,
            title = { Text("Temporary mediaPlayback foreground service?", style = MaterialTheme.typography.titleMedium, color = Paper) },
            text = {
                Text(
                    "This starts a real foreground service of type mediaPlayback and posts a temporary notification, " +
                        "then stops both after about one second. It does not play audio or create a media session. " +
                        "SqueezeClient uses this same service type for local playback. Run it only while parked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmFgs = false
                    viewModel.testMediaForegroundService()
                }) {
                    Text("START SERVICE", color = Amber, style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmFgs = false }) {
                    Text("CANCEL", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            },
        )
    }

    ScreenScroll {
        PageHeader(
            kicker = "Media / SqueezeClient port",
            title = "Media readiness",
            description = "Audio decoder, output, media-session, notification and foreground-service checks that " +
                "matter for the SqueezeClient local player.",
            aside = {
                if (media != null) {
                    SmallTag("${media.mimeSupport.count { entry -> entry.value }} MIMES", Teal)
                }
            },
        )

        if (media == null) {
            EmptyNotice("Run Quick Scan to inventory audio codecs, audio output, and media permissions.")
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
            "The diagnostic does not bundle Media3. It tests platform MediaCodec/AudioTrack, platform media " +
                "session classes, and the foreground-service path. The actual SqueezeClient Media3 pipeline still " +
                "needs a port build and a real audio session.",
        )
        Spacer(Modifier.height(14.dp))

        Panel(title = "Audio decoders", kicker = "MediaCodecList", accent = Teal) {
            media.mimeSupport.forEach { (mime, supported) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(mime, style = MaterialTheme.typography.bodySmall, color = Paper, fontFamily = FontFamily.Monospace)
                    Text(
                        if (supported) "DECODER" else "MISSING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (supported) Teal else Amber,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(10.dp))
            media.audioDecoders.take(30).forEach { codec ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(codec.name, style = MaterialTheme.typography.bodySmall, color = Paper)
                    Text(
                        "${codec.mimeTypes.joinToString()} · hw=${codec.hardwareAccelerated} " +
                            "sw=${codec.softwareOnly} max=${codec.maxSupportedInstances}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (media.audioDecoders.size > 30) {
                Text(
                    "Showing 30 of ${media.audioDecoders.size} audio decoders; the complete list is in JSON.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Faint,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Audio output path", kicker = "AudioManager / AudioTrack", accent = Teal) {
            KeyValue("Native sample rate", media.audioPath.nativeOutputSampleRate?.let { "$it Hz" } ?: "unknown")
            KeyValue("Frames per buffer", media.audioPath.framesPerBuffer?.toString() ?: "unknown")
            KeyValue("44.1k stereo min buffer", media.audioPath.minBufferBytes44100Stereo?.let { "$it B" } ?: "unsupported")
            KeyValue("48k stereo min buffer", media.audioPath.minBufferBytes48000Stereo?.let { "$it B" } ?: "unsupported")
            KeyValue("Direct PCM16", media.audioPath.directPlaybackSupported.toString())
            KeyValue("Outputs", media.audioPath.outputDeviceSummary.ifEmpty { listOf("none") }.joinToString())
            Spacer(Modifier.height(10.dp))
            ActionButton(
                label = if (state.activeBusy != null) "Busy" else "Test silent AudioTrack",
                onClick = viewModel::testSilentAudio,
                enabled = canAct,
                primary = true,
            )
            media.lastSilentAudioTest?.let { test ->
                Spacer(Modifier.height(8.dp))
                ResultRow(
                    title = "Silent PCM AudioTrack",
                    status = if (test.succeeded) {
                        com.sam2kb.lyriqprobe.core.model.ResultStatus.SUCCESS
                    } else {
                        com.sam2kb.lyriqprobe.core.model.ResultStatus.ERROR
                    },
                    summary = if (test.succeeded) {
                        "48 kHz stereo AudioTrack accepted ${test.bufferBytes} B; play state ${test.playState}."
                    } else {
                        "AudioTrack test failed: ${test.error ?: "unknown"}"
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Panel(title = "Media session and notifications", kicker = "Platform capability", accent = Amber) {
            KeyValue("MediaSession API", media.mediaSessionApiPresent.toString())
            KeyValue("MediaBrowserService", media.mediaBrowserServicePresent.toString())
            KeyValue("POST_NOTIFICATIONS granted", media.notificationPermissionGranted.toString())
            KeyValue("Notifications enabled", media.notificationsEnabled.toString())
            media.foregroundServicePermissions.forEach { (permission, granted) ->
                KeyValue(permission, granted.toString())
            }
            Spacer(Modifier.height(8.dp))
            if (!media.notificationPermissionGranted) {
                Notice(
                    NoticeTone.WARN,
                    "POST_NOTIFICATIONS is not granted. Grant it in Permissions before the foreground-service test " +
                        "if you want to verify the notification itself; the FGS result distinguishes service start from notification visibility.",
                )
                Spacer(Modifier.height(8.dp))
            }
            ActionButton(
                label = if (state.activeBusy != null) "Busy" else "Test mediaPlayback FGS",
                onClick = { confirmFgs = true },
                enabled = canAct,
                primary = true,
            )
            media.lastForegroundServiceTest?.let { test ->
                Spacer(Modifier.height(8.dp))
                ResultRow(
                    title = "mediaPlayback foreground service",
                    status = if (test.started && test.stopped) {
                        com.sam2kb.lyriqprobe.core.model.ResultStatus.SUCCESS
                    } else {
                        com.sam2kb.lyriqprobe.core.model.ResultStatus.ERROR
                    },
                    summary = buildString {
                        append("started=${test.started}, stopped=${test.stopped}, channel=${test.notificationChannelCreated}")
                        if (test.error != null) append(", error=${test.error}")
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        val active = state.report?.testResults.orEmpty().filter {
            it.module == com.sam2kb.lyriqprobe.core.model.ProbeModule.MEDIA &&
                it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.ACTIVE
        }
        if (active.isNotEmpty()) {
            Panel(title = "Active media tests", kicker = "Evidence", accent = Teal) {
                active.reversed().forEach { result ->
                    ResultRow(result.name, result.status, result.summary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "No media is recorded, no volume is changed, and no audio focus is requested. " +
                "The FGS test does not establish an actual media playback session.",
            style = MaterialTheme.typography.labelSmall,
            color = Faint,
        )
    }
}
