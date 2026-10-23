package com.sam2kb.lyriqprobe.core.report

import com.sam2kb.lyriqprobe.core.model.ProbeData
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestKind
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.model.AudioPathSnapshot
import com.sam2kb.lyriqprobe.core.model.LmsEndpointResult
import com.sam2kb.lyriqprobe.core.model.LmsNetworkSnapshot
import com.sam2kb.lyriqprobe.core.model.MediaSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReportFactoryTest {
    private val timestamp = Instant.parse("2026-10-23T12:00:00Z").toString()

    private fun result(
        id: String,
        status: ResultStatus,
        module: ProbeModule = ProbeModule.CAMERAS,
    ) = TestResult(
        id = id,
        module = module,
        kind = TestKind.DISCOVERY,
        name = "Test $id",
        status = status,
        summary = "Summary for $id",
        apiUsed = "test.api",
        elapsedMs = 4,
        timestamp = timestamp,
    )

    @Test
    fun summaryCountsEveryStatusAndCollectsErrors() {
        val data = ProbeData(
            results = listOf(
                result("a", ResultStatus.SUCCESS),
                result("b", ResultStatus.ERROR),
                result("c", ResultStatus.TIMEOUT),
                result("d", ResultStatus.DENIED),
            ),
        )
        val report = ReportFactory("1.0.0").build(data, Instant.parse(timestamp))

        assertEquals(4, report.summary.totalTests)
        assertEquals(1, report.summary.statusCounts["SUCCESS"])
        assertEquals(1, report.summary.statusCounts["ERROR"])
        assertEquals(1, report.summary.statusCounts["TIMEOUT"])
        assertEquals(1, report.summary.statusCounts["DENIED"])
        assertEquals(2, report.errors.size)
        assertTrue(report.summary.humanReadable.contains("LYRIQ AAOS CAPABILITY PROBE"))
        assertTrue(report.summary.keyFindings.any { it.contains("Report is read-only") })
    }

    @Test
    fun reportIsValidJsonWithRequiredTopLevelFields() {
        val report = ReportFactory("1.0.0").build(
            ProbeData(results = listOf(result("camera.enumerate", ResultStatus.NOT_EXPOSED))),
            Instant.parse(timestamp),
        )
        val json = ReportJson.encode(report)
        val root = Json.parseToJsonElement(json).jsonObject

        listOf(
            "schema_version",
            "app_version",
            "timestamp",
            "device",
            "android",
            "automotive",
            "permissions",
            "cameras",
            "vehicle_properties",
            "android_sensors",
            "storage",
            "connectivity",
            "media",
            "lms_network",
            "test_results",
            "errors",
            "summary",
        ).forEach { key ->
            assertNotNull("missing $key", root[key])
        }
        assertEquals("1.1", root["schema_version"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", root["app_version"]?.jsonPrimitive?.content)
        assertTrue(root["media"].toString() == "null")
        assertTrue(root["lms_network"].toString() == "null")
        assertTrue(json.contains("\"status\": \"NOT_EXPOSED\""))
    }

    @Test
    fun reportRoundTripsThroughSerializer() {
        val report = ReportFactory("1.0.0").build(
            ProbeData(results = listOf(result("a", ResultStatus.SUCCESS))),
            Instant.parse(timestamp),
        )
        val decoded = ReportJson.decode(ReportJson.encode(report))

        assertEquals(report.schemaVersion, decoded.schemaVersion)
        assertEquals(report.testResults.size, decoded.testResults.size)
        assertEquals(report.summary.totalTests, decoded.summary.totalTests)
        assertEquals(report.timestamp, decoded.timestamp)
    }

    @Test
    fun mediaAndLmsEvidenceAreSerializedAndSummarized() {
        val media = MediaSnapshot(
            audioDecoders = emptyList(),
            mimeSupport = mapOf("audio/flac" to true, "audio/ogg" to false),
            audioPath = AudioPathSnapshot(
                nativeOutputSampleRate = 48_000,
                framesPerBuffer = 192,
                minBufferBytes44100Stereo = 3_528,
                minBufferBytes48000Stereo = 3_840,
                directPlaybackSupported = mapOf("pcm16/48000/stereo" to true),
                outputDeviceSummary = listOf("speaker:type=2"),
            ),
            mediaSessionApiPresent = true,
            mediaBrowserServicePresent = true,
            notificationPermissionGranted = true,
            notificationsEnabled = true,
            foregroundServicePermissions = mapOf(
                "FOREGROUND_SERVICE" to true,
                "FOREGROUND_SERVICE_MEDIA_PLAYBACK" to true,
            ),
            note = "test",
        )
        val lms = LmsNetworkSnapshot(
            internetPermissionGranted = true,
            localNetworkPermissionDefined = false,
            localNetworkPermissionGranted = false,
            nearbyWifiPermissionDefined = true,
            cleartextTrafficPermitted = true,
            endpointKind = "PRIVATE_IPV4",
            lastTest = listOf(
                LmsEndpointResult("http", 9000, true, true, 12, null, null),
                LmsEndpointResult("slimproto", 3483, true, false, null, "java.net.ConnectException", "Connection refused or unreachable."),
            ),
            note = "test",
        )
        val report = ReportFactory("1.0.0").build(
            ProbeData(media = media, lmsNetwork = lms),
            Instant.parse(timestamp),
        )
        val root = Json.parseToJsonElement(ReportJson.encode(report)).jsonObject

        assertEquals("48000", root["media"]?.jsonObject?.get("audioPath")?.jsonObject
            ?.get("nativeOutputSampleRate")?.jsonPrimitive?.content)
        assertEquals("PRIVATE_IPV4", root["lms_network"]?.jsonObject?.get("endpointKind")?.jsonPrimitive?.content)
        assertTrue(report.summary.keyFindings.any { it.contains("Audio decoders found", ignoreCase = true) })
        assertTrue(report.summary.keyFindings.any { it.contains("LMS readiness", ignoreCase = true) })
    }

    @Test
    fun emptyZeroCameraReportDoesNotClaimHardwareAbsence() {
        val report = ReportFactory("1.0.0").build(ProbeData(), Instant.parse(timestamp))
        val findings = report.summary.keyFindings.joinToString(" ")

        assertTrue(findings.contains("camera inventory did not complete", ignoreCase = true))
    }
}
