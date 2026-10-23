package com.sam2kb.lyriqprobe.core.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.sam2kb.lyriqprobe.core.model.PermissionEntry
import com.sam2kb.lyriqprobe.core.model.PermissionSnapshot
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestKind
import com.sam2kb.lyriqprobe.core.model.TestMeta
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.permission.PermissionClassifier
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta

data class PermissionCatalogItem(
    val name: String,
    val label: String,
    val purpose: String,
    val note: String? = null,
)

class PermissionProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val packageManager = context.packageManager

    suspend fun scan(): ProbeSection<PermissionSnapshot> {
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val declared = packageInfo.requestedPermissions.orEmpty().toSet()
        val entries = catalog.map { candidate -> inspect(candidate, declared) }
        val runtimeRequestable = entries.filter { it.requestable && !it.granted }.map { it.name }
        val snapshot = PermissionSnapshot(
            entries = entries,
            runtimeRequestable = runtimeRequestable,
            declaredButUnavailable = entries.filter { it.declared && !it.existsOnDevice }.map { it.name },
            protectedPermissions = entries.filter {
                it.existsOnDevice && (it.protectionLevel == "SIGNATURE" ||
                    "PRIVILEGED" in it.protectionFlags)
            }.map { it.name },
            deniedRuntimePermissions = entries.filter {
                it.declared && it.protectionLevel == "DANGEROUS" && !it.granted
            }.map { it.name },
            catalogNote = "Curated catalog of platform and android.car permissions relevant to this probe. " +
                "OEM-private permissions are not enumerated and the catalog is not claimed to be exhaustive.",
        )

        val results = mutableListOf<TestResult>()
        results += harness.run(
            discoveryMeta(
                "permissions.catalog",
                ProbeModule.PERMISSIONS,
                "Permission availability matrix",
                "PackageManager.getPermissionInfo/checkSelfPermission",
            ),
        ) {
            TestOutcome(
                status = ResultStatus.AVAILABLE,
                summary = "${entries.count { it.existsOnDevice }} of ${entries.size} catalogued permissions exist on this device; " +
                    "${entries.count { it.granted }} are granted.",
                details = entries.joinToString("\n") {
                    "${it.label}: ${it.grantState} (declared=${it.declared}, exists=${it.existsOnDevice}, " +
                        "protection=${it.protectionLevel}${if (it.protectionFlags.isEmpty()) "" else "|" + it.protectionFlags.joinToString("|")})"
                },
                measurements = mapOf(
                    "catalog_size" to entries.size.toString(),
                    "granted" to entries.count { it.granted }.toString(),
                    "requestable_now" to runtimeRequestable.size.toString(),
                ),
            )
        }
        results += harness.run(
            discoveryMeta(
                "permissions.camera",
                ProbeModule.PERMISSIONS,
                "Camera permission",
                "android.permission.CAMERA",
            ),
        ) {
            val entry = entries.first { it.name == Manifest.permission.CAMERA }
            val status = when {
                !entry.existsOnDevice -> ResultStatus.UNSUPPORTED
                entry.granted -> ResultStatus.SUCCESS
                entry.declared -> ResultStatus.AVAILABLE
                else -> ResultStatus.DENIED
            }
            TestOutcome(
                status = status,
                summary = if (entry.granted) {
                    "CAMERA is granted."
                } else {
                    "CAMERA is not granted; request it from the Permissions tab before opening a camera."
                },
                measurements = mapOf(
                    "declared" to entry.declared.toString(),
                    "requestable" to entry.requestable.toString(),
                ),
            )
        }
        results += harness.run(
            discoveryMeta(
                "permissions.automotive",
                ProbeModule.PERMISSIONS,
                "Automotive read permissions",
                "android.car.permission.*",
            ),
        ) {
            val automotive = entries.filter { it.name.startsWith("android.car.permission.") }
            val granted = automotive.filter { it.granted }
            TestOutcome(
                status = when {
                    automotive.none { it.existsOnDevice } -> ResultStatus.UNSUPPORTED
                    granted.isEmpty() -> ResultStatus.DENIED
                    else -> ResultStatus.AVAILABLE
                },
                summary = "${granted.size} of ${automotive.size} catalogued automotive read permissions are granted.",
                details = automotive.joinToString("\n") {
                    "${it.label}: exists=${it.existsOnDevice}, declared=${it.declared}, granted=${it.granted}, protection=${it.protectionLevel}"
                },
                measurements = mapOf("automotive_permissions_granted" to granted.size.toString()),
            )
        }
        results += harness.run(
            discoveryMeta(
                "permissions.protected",
                ProbeModule.PERMISSIONS,
                "Privileged and signature permissions",
                "PermissionInfo.protectionLevel",
            ),
        ) {
            val protected = entries.filter {
                it.existsOnDevice && (it.protectionLevel == "SIGNATURE" || "PRIVILEGED" in it.protectionFlags)
            }
            TestOutcome(
                status = if (protected.isEmpty()) ResultStatus.SUCCESS else ResultStatus.PRIVILEGED_ONLY,
                summary = "${protected.size} catalogued permission(s) are signature-only or privileged; " +
                    "this app does not attempt to obtain them.",
                details = protected.joinToString("\n") { "${it.label}: ${it.protectionLevel} ${it.protectionFlags}" },
            )
        }
        if (runtimeRequestable.isNotEmpty()) {
            results += harness.run(
                discoveryMeta(
                    "permissions.runtime_requestable",
                    ProbeModule.PERMISSIONS,
                    "Runtime-requestable permissions",
                    "Context.requestPermissions",
                ),
            ) {
                TestOutcome(
                    ResultStatus.AVAILABLE,
                    "${runtimeRequestable.size} dangerous permission(s) can be requested individually from the Permissions tab.",
                    details = runtimeRequestable.joinToString("\n"),
                )
            }
        }
        return ProbeSection(snapshot, results)
    }

    suspend fun recordRequestResult(permission: String, granted: Boolean): TestResult {
        val label = permission.substringAfterLast('.')
        return harness.run(
            TestMeta(
                id = "permissions.request.$label",
                module = ProbeModule.PERMISSIONS,
                kind = TestKind.ACTIVE,
                name = "Request $label",
                apiUsed = "Activity.requestPermissions",
                timeoutMs = 10_000,
            ),
        ) {
            TestOutcome(
                status = if (granted) ResultStatus.SUCCESS else ResultStatus.DENIED,
                summary = if (granted) {
                    "$label was granted by the user."
                } else {
                    "$label request returned denied. Signature, privileged, user-fixed, or policy-blocked permissions cannot be obtained by request."
                },
                measurements = mapOf("permission" to permission, "granted" to granted.toString()),
            )
        }
    }

    private fun inspect(
        candidate: PermissionCatalogItem,
        declared: Set<String>,
    ): PermissionEntry {
        val info = try {
            packageManager.getPermissionInfo(candidate.name, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val exists = info != null
        val protection = info?.let { PermissionClassifier.classify(it.protectionLevel) }
        val isDeclared = candidate.name in declared
        val granted = exists && isDeclared &&
            context.checkSelfPermission(candidate.name) == PackageManager.PERMISSION_GRANTED
        val base = protection?.base ?: "UNKNOWN"
        val requestable = exists && isDeclared && protection?.dangerous == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val state = when {
            !exists -> "NOT_ON_DEVICE"
            granted -> "GRANTED"
            base == "SIGNATURE" || protection?.privileged == true -> "PRIVILEGED_ONLY"
            isDeclared -> "DECLARED_NOT_GRANTED"
            else -> "NOT_DECLARED"
        }
        val generatedNote = when {
            !exists -> "This permission name is not defined by the installed platform."
            !isDeclared && (base == "SIGNATURE" || protection?.privileged == true) ->
                "Signature or privileged protection; an ordinary Play-installed app cannot obtain it."
            !isDeclared -> "Catalogued for comparison only; this build does not request it."
            requestable && !granted -> "Can be requested at runtime with an explicit user action."
            else -> null
        }
        return PermissionEntry(
            name = candidate.name,
            label = candidate.label,
            group = info?.group,
            existsOnDevice = exists,
            declared = isDeclared,
            granted = granted,
            protectionLevel = base,
            protectionLevelRaw = info?.protectionLevel,
            protectionFlags = protection?.flags.orEmpty(),
            requestable = requestable,
            grantState = state,
            purpose = candidate.purpose,
            note = generatedNote ?: candidate.note,
        )
    }

    companion object {
        val catalog: List<PermissionCatalogItem> = listOf(
            PermissionCatalogItem(
                Manifest.permission.CAMERA,
                "CAMERA",
                "Open ordinary Camera2 devices exposed to third-party apps.",
            ),
            PermissionCatalogItem(
                "android.permission.SYSTEM_CAMERA",
                "SYSTEM_CAMERA",
                "Access system cameras reserved for the vehicle camera stack.",
                note = "Harmless to catalogue, but not declared by this app: it is signature/privileged and is never requested.",
            ),
            PermissionCatalogItem(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "ACCESS_COARSE_LOCATION",
                "Coarse location permission presence; this build neither declares nor reads location.",
            ),
            PermissionCatalogItem(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "ACCESS_FINE_LOCATION",
                "Precise location permission presence; this build neither declares nor reads location.",
            ),
            PermissionCatalogItem(
                Manifest.permission.BLUETOOTH_CONNECT,
                "BLUETOOTH_CONNECT",
                "Read Bluetooth adapter state on Android 12 and newer; no device enumeration.",
            ),
            PermissionCatalogItem(
                Manifest.permission.BLUETOOTH_SCAN,
                "BLUETOOTH_SCAN",
                "Bluetooth scan permission availability; this build does not request or perform scans.",
            ),
            PermissionCatalogItem(
                Manifest.permission.ACCESS_NETWORK_STATE,
                "ACCESS_NETWORK_STATE",
                "Read transport and capability metadata without payload access.",
            ),
            PermissionCatalogItem(
                Manifest.permission.ACCESS_WIFI_STATE,
                "ACCESS_WIFI_STATE",
                "Read Wi-Fi enabled state; no SSID, BSSID, IP or MAC is collected.",
            ),
            PermissionCatalogItem(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "READ_EXTERNAL_STORAGE",
                "Legacy broad storage permission, catalogued only.",
                note = "Not declared. Storage diagnostics use the Storage Access Framework instead.",
            ),
            PermissionCatalogItem(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "WRITE_EXTERNAL_STORAGE",
                "Legacy broad write permission, catalogued only.",
                note = "Not declared. Storage diagnostics write only inside a user-selected SAF tree.",
            ),
            PermissionCatalogItem(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                "MANAGE_EXTERNAL_STORAGE",
                "All-files access, catalogued to show that it is not used.",
                note = "Not declared and not requested.",
            ),
            PermissionCatalogItem(
                Manifest.permission.READ_MEDIA_IMAGES,
                "READ_MEDIA_IMAGES",
                "Scoped media read permission, catalogued only.",
            ),
            PermissionCatalogItem(
                Manifest.permission.READ_MEDIA_VIDEO,
                "READ_MEDIA_VIDEO",
                "Scoped media read permission, catalogued only.",
            ),
            PermissionCatalogItem(
                Manifest.permission.RECORD_AUDIO,
                "RECORD_AUDIO",
                "Microphone access, catalogued to show that it is not used.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_INFO",
                "CAR_INFO",
                "Read make, model, year and other static vehicle information (not VIN).",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_ENERGY",
                "CAR_ENERGY",
                "Read EV battery level, charge state and energy properties.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_ENERGY_PORTS",
                "CAR_ENERGY_PORTS",
                "Read EV charge-port connection and port location.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_SPEED",
                "CAR_SPEED",
                "Read vehicle speed properties.",
            ),
            PermissionCatalogItem(
                "android.car.permission.READ_CAR_DISPLAY_UNITS",
                "READ_CAR_DISPLAY_UNITS",
                "Read the vehicle's preferred display units.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_MILEAGE",
                "CAR_MILEAGE",
                "Read odometer and mileage properties.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_SENSORS",
                "CAR_SENSORS",
                "Read vehicle sensor properties on platforms that expose this permission.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_IDENTIFICATION",
                "CAR_IDENTIFICATION",
                "Vehicle identification properties, catalogued for protection-level reporting only.",
                note = "Not declared. VIN is neither requested nor exported.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CAR_POWERTRAIN",
                "CAR_POWERTRAIN",
                "Powertrain state such as gear, catalogued only.",
                note = "Not declared; the probe does not read gear or drivetrain state.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CONTROL_CAR_ENERGY",
                "CONTROL_CAR_ENERGY",
                "Write access to charging controls, catalogued to show that it is not used.",
                note = "Not declared. This probe never writes vehicle properties.",
            ),
            PermissionCatalogItem(
                "android.car.permission.CONTROL_CAR_CLIMATE",
                "CONTROL_CAR_CLIMATE",
                "Write access to HVAC controls, catalogued to show that it is not used.",
                note = "Not declared. This probe never writes vehicle properties.",
            ),
        )
    }
}
