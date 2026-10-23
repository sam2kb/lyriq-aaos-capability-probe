package com.sam2kb.lyriqprobe.core.report

import com.sam2kb.lyriqprobe.core.model.ProbeData
import com.sam2kb.lyriqprobe.core.model.ProbeError
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.core.model.ReportSummary
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.util.formatBytes
import com.sam2kb.lyriqprobe.core.util.humanTimestamp
import com.sam2kb.lyriqprobe.core.util.utcTimestamp
import java.time.Instant

class ReportFactory(private val appVersion: String) {

    fun build(data: ProbeData, now: Instant = Instant.now()): ProbeReport {
        val summary = summarize(data, now)
        val errors = (data.errors + data.results.toProbeErrors()).distinctBy {
            "${it.testId}|${it.exceptionClass}|${it.message}"
        }
        return ProbeReport(
            schemaVersion = ReportJson.SCHEMA_VERSION,
            appVersion = appVersion,
            timestamp = utcTimestamp(now),
            app = data.app,
            device = data.device,
            android = data.android,
            automotive = data.automotive,
            permissions = data.permissions,
            cameras = data.cameras,
            vehicleProperties = data.vehicle,
            androidSensors = data.sensors,
            storage = data.storage,
            connectivity = data.connectivity,
            vpn = data.vpn,
            drivingState = data.driving,
            carAppLibrary = data.carAppLibrary,
            summary = summary,
            testResults = data.results,
            errors = errors,
        )
    }

    fun summarize(data: ProbeData, now: Instant = Instant.now()): ReportSummary {
        val counts = ResultStatus.entries.associate { status ->
            status.name to data.results.count { it.status == status }
        }
        val keyFindings = keyFindings(data)
        val human = buildHumanSummary(data, counts, keyFindings, now)
        return ReportSummary(
            totalTests = data.results.size,
            statusCounts = counts,
            cameraCount = data.cameras?.cameraCount ?: 0,
            vehiclePropertyCount = data.vehicle?.propertyCount ?: 0,
            sensorCount = data.sensors?.sensors?.size ?: 0,
            storageVolumeCount = data.storage?.volumes?.size ?: 0,
            keyFindings = keyFindings,
            humanReadable = human,
        )
    }

    private fun keyFindings(data: ProbeData): List<String> = buildList {
        val camera = data.cameras
        if (camera == null) {
            add("Camera inventory did not complete.")
        } else {
            when {
                camera.cameraCount == 0 -> add(
                    "No cameras are exposed to this app through Camera2. " +
                        "This does not prove the vehicle has no cameras; factory cameras may be system-only.",
                )
                else -> add(
                    "${camera.cameraCount} Camera2 camera(s) enumerated " +
                        "(${camera.externalCameraCount} external). Enumeration does not prove capture access.",
                )
            }
        }
        val successfulOpens = data.results.count {
            it.kind == com.sam2kb.lyriqprobe.core.model.TestKind.ACTIVE &&
                it.id.startsWith("camera.open.") &&
                it.status == ResultStatus.SUCCESS
        }
        if (successfulOpens > 0) {
            add("$successfulOpens camera open/close test(s) succeeded.")
        }
        val frameCapture = data.results.any {
            it.id.startsWith("camera.frame.") && it.status == ResultStatus.SUCCESS
        }
        if (frameCapture) {
            add("At least one single-frame capture test succeeded.")
        }

        val vehicle = data.vehicle
        if (vehicle == null) {
            add("Vehicle-property inventory did not complete.")
        } else {
            add(
                "${vehicle.propertyCount} vehicle properties are visible to this app via " +
                    "CarPropertyManager.getPropertyList().",
            )
            val read = vehicle.readings.count { it.status == ResultStatus.SUCCESS }
            if (vehicle.readings.isNotEmpty()) {
                add("$read of ${vehicle.readings.size} requested vehicle values were read.")
            }
        }

        data.sensors?.let {
            add("${it.sensors.size} Android sensor(s) are exposed to the app.")
        }
        data.storage?.let {
            add(
                "${it.volumes.size} storage volume(s) enumerated; " +
                    "external app storage state is ${it.externalStorageState}.",
            )
            it.testRecord?.let { record ->
                add(
                    if (record.success) {
                        "SAF create/write/read/delete test passed on \"${record.displayName}\"."
                    } else {
                        "SAF create/write/read/delete test failed on \"${record.displayName}\"."
                    },
                )
            }
        }
        data.permissions?.let {
            add(
                "${it.entries.count { entry -> entry.granted }} of " +
                    "${it.entries.size} catalogued permissions are granted; " +
                    "${it.protectedPermissions.size} are privileged or signature-only.",
            )
        }
        data.vpn?.let { vpn ->
            when {
                vpn.interfaceEstablished == true && vpn.interfaceClosed == true ->
                    add("A temporary VpnService TUN interface was established and closed with no routes or DNS configured.")
                vpn.authorizationState.contains("DENIED", ignoreCase = true) ->
                    add("VPN authorization was denied; no TUN interface was created.")
                !vpn.serviceDeclared ->
                    add("No VpnService component is declared in this build.")
                else ->
                    add("VPN capability is ${vpn.authorizationState.lowercase().replace('_', ' ')}; see the VPN section.")
            }
        }
        data.driving?.let {
            if (it.available) {
                add(
                    if (it.requiresDistractionOptimization == true) {
                        "AAOS reports a restricted driving state; active tests must remain disabled."
                    } else {
                        "AAOS reports no distraction-optimization requirement at scan time."
                    },
                )
            }
        }
        add(
            "Report is read-only: no vehicle writes, no camera recording, no background capture, " +
                "no VIN, precise location, or device serial number.",
        )
    }

    private fun buildHumanSummary(
        data: ProbeData,
        counts: Map<String, Int>,
        keyFindings: List<String>,
        now: Instant,
    ): String = buildString {
        appendLine("LYRIQ AAOS CAPABILITY PROBE")
        appendLine("Generated: ${humanTimestamp(now)}")
        appendLine("Schema: ${ReportJson.SCHEMA_VERSION}    App: $appVersion")
        data.device?.let {
            appendLine("Vehicle/device: ${it.manufacturer} ${it.model} (${it.device})")
            appendLine("RAM: ${formatBytes(it.availableRamBytes)} free of ${formatBytes(it.totalRamBytes)}")
        }
        data.android?.let {
            appendLine("Android: ${it.release} (API ${it.sdkInt}), patch ${it.securityPatch ?: "unknown"}")
        }
        appendLine()
        appendLine("KEY FINDINGS")
        keyFindings.forEach { appendLine("- $it") }
        appendLine()
        appendLine("TEST STATUS")
        counts.filterValues { it > 0 }.forEach { (status, count) -> appendLine("- $status: $count") }
        appendLine()
        appendLine("This is a diagnostic report, not proof that a camera stream is usable for recording.")
        appendLine("Vehicle and camera results are specific to the installed build and permissions.")
    }

    private fun List<TestResult>.toProbeErrors(): List<ProbeError> = filter {
        it.status == ResultStatus.ERROR || it.status == ResultStatus.TIMEOUT
    }.map {
        ProbeError(
            module = it.module,
            testId = it.id,
            message = it.summary,
            exceptionClass = it.exceptionClass,
            timestamp = it.timestamp,
        )
    }
}
