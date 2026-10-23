package com.sam2kb.lyriqprobe.core.report

import com.sam2kb.lyriqprobe.core.model.ProbeData
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestKind
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.model.VpnCapabilitySnapshot
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
            "vpn",
            "test_results",
            "errors",
            "summary",
        ).forEach { key ->
            assertNotNull("missing $key", root[key])
        }
        assertEquals("1.0", root["schema_version"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", root["app_version"]?.jsonPrimitive?.content)
        assertTrue(root["vpn"].toString() == "null")
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
    fun vpnEvidenceIsSerializedAndSummarized() {
        val vpn = VpnCapabilitySnapshot(
            apiAvailable = true,
            serviceDeclared = true,
            bindPermissionProtected = true,
            serviceExported = false,
            vpnIntentFilterPresent = true,
            authorizationState = "AUTHORIZED",
            consentDialogRequested = true,
            interfaceEstablished = true,
            interfaceClosed = true,
            sessionName = "LYRIQ AAOS capability probe",
            addressesAdded = listOf("10.254.253.1/32"),
            routesAdded = emptyList(),
            dnsServers = emptyList(),
            allowedApplications = emptyList(),
            durationMs = 42,
            error = null,
            note = "No traffic routed.",
        )
        val report = ReportFactory("1.0.0").build(
            ProbeData(vpn = vpn),
            Instant.parse(timestamp),
        )
        val root = Json.parseToJsonElement(ReportJson.encode(report)).jsonObject

        assertEquals("AUTHORIZED", root["vpn"]?.jsonObject?.get("authorizationState")?.jsonPrimitive?.content)
        assertEquals("true", root["vpn"]?.jsonObject?.get("interfaceEstablished")?.jsonPrimitive?.content)
        assertTrue(report.summary.keyFindings.any { it.contains("temporary VpnService TUN", ignoreCase = true) })
    }

    @Test
    fun emptyZeroCameraReportDoesNotClaimHardwareAbsence() {
        val report = ReportFactory("1.0.0").build(ProbeData(), Instant.parse(timestamp))
        val findings = report.summary.keyFindings.joinToString(" ")

        assertTrue(findings.contains("camera inventory did not complete", ignoreCase = true))
    }
}
