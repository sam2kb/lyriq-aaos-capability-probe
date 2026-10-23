package com.sam2kb.lyriqprobe.core.probe

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodecList
import android.os.Build
import com.sam2kb.lyriqprobe.core.model.AudioPathSnapshot
import com.sam2kb.lyriqprobe.core.model.MediaCodecInfo as MediaCodecSnapshot
import com.sam2kb.lyriqprobe.core.model.MediaSnapshot
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.SilentAudioTestResult
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SilentAudioProbeResult(
    val record: SilentAudioTestResult?,
    val result: TestResult,
)

/**
 * AAOS port-readiness checks for the SqueezeClient local player and media session.
 *
 * The active audio test writes silent PCM to an AudioTrack. It does not play media,
 * change volume, request audio focus, or record audio.
 */
class MediaProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    suspend fun scan(): ProbeSection<MediaSnapshot> {
        val codecs = runCatching { audioCodecs() }.getOrDefault(emptyList())
        val mimeSupport = relevantMimeTypes.associateWith { mime ->
            val aliases = when (mime) {
                "audio/ogg" -> listOf("audio/ogg", "audio/vorbis")
                "audio/mp4a-latm" -> listOf("audio/mp4a-latm", "audio/aac")
                else -> listOf(mime)
            }
            codecs.any { codec -> codec.mimeTypes.any { it in aliases } }
        }
        val audioPath = readAudioPath()
        val notificationPermission = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val notificationsEnabled = context.getSystemService(NotificationManager::class.java)
            ?.areNotificationsEnabled() ?: false
        val fgsPermissions = mapOf(
            "FOREGROUND_SERVICE" to isPermissionGranted("android.permission.FOREGROUND_SERVICE"),
            "FOREGROUND_SERVICE_MEDIA_PLAYBACK" to
                isPermissionGranted("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
        )
        val mediaSession = classAvailable("android.media.session.MediaSession")
        val mediaBrowser = classAvailable("android.service.media.MediaBrowserService")
        val snapshot = MediaSnapshot(
            audioDecoders = codecs,
            mimeSupport = mimeSupport,
            audioPath = audioPath,
            mediaSessionApiPresent = mediaSession,
            mediaBrowserServicePresent = mediaBrowser,
            notificationPermissionGranted = notificationPermission,
            notificationsEnabled = notificationsEnabled,
            foregroundServicePermissions = fgsPermissions,
            note = "Audio codecs are platform MediaCodec decoders. The active smoke test writes silent PCM " +
                "through AudioTrack; it does not exercise Media3, decoding, audio focus, or actual playback.",
        )

        val results = mutableListOf<TestResult>()
        results += harness.run(
            discoveryMeta(
                "media.codecs",
                ProbeModule.MEDIA,
                "Audio decoder inventory",
                "MediaCodecList",
            ),
        ) {
            val supported = mimeSupport.filterValues { it }.keys
            TestOutcome(
                status = if (codecs.isEmpty()) ResultStatus.NOT_EXPOSED else ResultStatus.AVAILABLE,
                summary = "${codecs.size} audio decoder codec(s); " +
                    "${supported.size}/${relevantMimeTypes.size} relevant MIME types have a decoder.",
                details = codecs.joinToString("\n") {
                    "${it.name}: ${it.mimeTypes.joinToString()} hw=${it.hardwareAccelerated} sw=${it.softwareOnly}"
                },
                measurements = supported.associateWith { "true" },
            )
        }
        results += harness.run(
            discoveryMeta(
                "media.audio_path",
                ProbeModule.MEDIA,
                "Audio output path",
                "AudioManager / AudioTrack",
            ),
        ) {
            TestOutcome(
                status = if (audioPath.nativeOutputSampleRate == null &&
                    audioPath.minBufferBytes48000Stereo == null
                ) {
                    ResultStatus.UNAVAILABLE
                } else {
                    ResultStatus.AVAILABLE
                },
                summary = "Native ${audioPath.nativeOutputSampleRate ?: "?"} Hz output, " +
                    "${audioPath.framesPerBuffer ?: "?"} frames/buffer, " +
                    "${audioPath.outputDeviceSummary.size} output device(s).",
                details = buildString {
                    appendLine("44.1 kHz stereo min buffer: ${audioPath.minBufferBytes44100Stereo} bytes")
                    appendLine("48 kHz stereo min buffer: ${audioPath.minBufferBytes48000Stereo} bytes")
                    appendLine("Direct PCM16 playback: ${audioPath.directPlaybackSupported}")
                    appendLine("Outputs: ${audioPath.outputDeviceSummary}")
                },
            )
        }
        results += harness.run(
            discoveryMeta(
                "media.session_apis",
                ProbeModule.MEDIA,
                "MediaSession and MediaBrowserService availability",
                "platform class availability",
            ),
        ) {
            TestOutcome(
                status = if (mediaSession && mediaBrowser) ResultStatus.AVAILABLE else ResultStatus.UNSUPPORTED,
                summary = "MediaSession=$mediaSession, MediaBrowserService=$mediaBrowser.",
                details = "Media3's MediaSessionService is not bundled in this probe. This only checks the platform " +
                    "media session/browser classes that a ported app builds on.",
            )
        }
        results += harness.run(
            discoveryMeta(
                "media.notifications",
                ProbeModule.MEDIA,
                "Notification and foreground-service permissions",
                "NotificationManager / PackageManager",
            ),
        ) {
            TestOutcome(
                status = if (notificationPermission) ResultStatus.AVAILABLE else ResultStatus.DENIED,
                summary = "POST_NOTIFICATIONS granted=$notificationPermission; " +
                    "notifications enabled=$notificationsEnabled; media FGS permission=" +
                    "${fgsPermissions["FOREGROUND_SERVICE_MEDIA_PLAYBACK"]}.",
                details = fgsPermissions.entries.joinToString("\n") { "${it.key}=${it.value}" },
            )
        }
        return ProbeSection(snapshot, results)
    }

    suspend fun testSilentAudio(): SilentAudioProbeResult = withContext(Dispatchers.IO) {
        var record: SilentAudioTestResult? = null
        val result = harness.run(
            activeMeta(
                id = "media.silent_audio",
                module = ProbeModule.MEDIA,
                name = "One-second silent PCM AudioTrack test",
                apiUsed = "AudioTrack",
                timeoutMs = 12_000,
            ),
        ) {
            val sampleRate = 48_000
            val channelMask = AudioFormat.CHANNEL_OUT_STEREO
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                record = SilentAudioTestResult(
                    attempted = true,
                    succeeded = false,
                    sampleRate = sampleRate,
                    channelCount = 2,
                    bufferBytes = 0,
                    playState = null,
                    error = "AudioTrack.getMinBufferSize returned $minBuffer.",
                )
                return@run TestOutcome(
                    status = ResultStatus.UNSUPPORTED,
                    summary = "AudioTrack did not report a usable minimum buffer.",
                )
            }
            val bufferBytes = maxOf(minBuffer * 2, 8_192)
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelMask)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(bufferBytes)
                    .build()
                } catch (throwable: Throwable) {
                    record = SilentAudioTestResult(
                        attempted = true,
                        succeeded = false,
                        sampleRate = sampleRate,
                        channelCount = 2,
                        bufferBytes = bufferBytes,
                        playState = null,
                        error = "${throwable.javaClass.simpleName}: ${throwable.message}",
                    )
                    return@run TestOutcome(
                        status = ResultStatus.ERROR,
                        summary = "AudioTrack creation failed: ${throwable.javaClass.simpleName}: ${throwable.message}",
                        exceptionClass = throwable.javaClass.name,
                        exceptionMessage = throwable.message,
                    )
                }
            try {
                track.play()
                val silence = ShortArray(2_048)
                var written = 0
                repeat(4) {
                    val n = track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
                    if (n > 0) written += n
                }
                delay(600)
                val state = track.playState
                val succeeded = written > 0 && state == AudioTrack.PLAYSTATE_PLAYING
                record = SilentAudioTestResult(
                    attempted = true,
                    succeeded = succeeded,
                    sampleRate = sampleRate,
                    channelCount = 2,
                    bufferBytes = bufferBytes,
                    playState = playStateName(state),
                    error = null,
                )
                TestOutcome(
                    status = if (succeeded) {
                        ResultStatus.SUCCESS
                    } else {
                        ResultStatus.ERROR
                    },
                    summary = "AudioTrack accepted silent PCM at 48 kHz stereo " +
                        "(${written * 2} bytes written, state ${playStateName(state)}).",
                    details = "No audio content was emitted, no volume was changed, and no audio focus was requested.",
                    measurements = mapOf(
                        "sample_rate" to sampleRate.toString(),
                        "channels" to "2",
                        "buffer_bytes" to bufferBytes.toString(),
                    ),
                )
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
        SilentAudioProbeResult(record, result)
    }

    private fun audioCodecs(): List<MediaCodecSnapshot> {
        val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        return infos.asSequence()
            .filter { !it.isEncoder }
            .mapNotNull { codec ->
                val mimes = codec.supportedTypes.filter { it.startsWith("audio/") }.sorted()
                if (mimes.isEmpty()) return@mapNotNull null
                MediaCodecSnapshot(
                    name = codec.name,
                    mimeTypes = mimes,
                    hardwareAccelerated = runCatching { codec.isHardwareAccelerated }.getOrNull(),
                    softwareOnly = runCatching { codec.isSoftwareOnly }.getOrNull(),
                    maxSupportedInstances = null,
                )
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
            .toList()
    }

    private fun readAudioPath(): AudioPathSnapshot {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val direct = linkedMapOf<String, Boolean>()
        listOf(44_100, 48_000).forEach { rate ->
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            direct["pcm16/${rate}/stereo"] = runCatching {
                AudioTrack.isDirectPlaybackSupported(format, attributes)
            }.getOrDefault(false)
        }
        val outputs = runCatching {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                ?.map { "${it.productName ?: "device"}:type=${it.type}" }
                .orEmpty()
        }.getOrDefault(emptyList())
        return AudioPathSnapshot(
            nativeOutputSampleRate = audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull(),
            framesPerBuffer = audioManager
                ?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull(),
            minBufferBytes44100Stereo = AudioTrack.getMinBufferSize(
                44_100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).takeIf { it > 0 },
            minBufferBytes48000Stereo = AudioTrack.getMinBufferSize(
                48_000,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).takeIf { it > 0 },
            directPlaybackSupported = direct,
            outputDeviceSummary = outputs,
        )
    }

    private fun isPermissionGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun classAvailable(name: String): Boolean =
        runCatching { Class.forName(name, false, context.classLoader) }.isSuccess

    private fun playStateName(state: Int): String = when (state) {
        AudioTrack.PLAYSTATE_PLAYING -> "PLAYING"
        AudioTrack.PLAYSTATE_PAUSED -> "PAUSED"
        AudioTrack.PLAYSTATE_STOPPED -> "STOPPED"
        else -> "STATE_$state"
    }

    private companion object {
        val relevantMimeTypes = listOf(
            "audio/mpeg",
            "audio/flac",
            "audio/ogg",
            "audio/vorbis",
            "audio/opus",
            "audio/mp4a-latm",
            "audio/aac",
            "audio/raw",
        )
    }
}
