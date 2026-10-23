package com.sam2kb.lyriqprobe.core.probe

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.sam2kb.lyriqprobe.core.car.CarAccess
import com.sam2kb.lyriqprobe.core.car.managerPresence
import com.sam2kb.lyriqprobe.core.model.AndroidSnapshot
import com.sam2kb.lyriqprobe.core.model.AppSnapshot
import com.sam2kb.lyriqprobe.core.model.AutomotiveSnapshot
import com.sam2kb.lyriqprobe.core.model.DeviceSnapshot
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import com.sam2kb.lyriqprobe.core.util.formatBytes
import java.util.Locale
import java.util.TimeZone

data class SystemScan(
    val app: AppSnapshot,
    val device: DeviceSnapshot,
    val android: AndroidSnapshot,
    val automotive: AutomotiveSnapshot,
)

class SystemProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val packageManager: PackageManager = context.packageManager

    fun readAppInfo(): AppSnapshot {
        @Suppress("DEPRECATION")
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requested = packageInfo.requestedPermissions.orEmpty()
        val granted = requested.filter {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }.sorted()
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                packageManager.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { packageManager.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        return AppSnapshot(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = packageInfo.longVersionCode,
            installer = installer,
            minSdk = packageInfo.applicationInfo?.minSdkVersion ?: 0,
            targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0,
            debuggable = packageInfo.applicationInfo?.let {
                it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
            } ?: false,
            grantedPermissions = granted,
        )
    }

    suspend fun scan(carAccess: CarAccess?): ProbeSection<SystemScan> {
        val app = readAppInfo()
        val features = packageManager.systemAvailableFeatures
            .orEmpty()
            .mapNotNull { it.name }
            .distinct()
            .sorted()
        val selectedChecks = selectedFeatureChecks.associateWith {
            packageManager.hasSystemFeature(it)
        }
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val stat = runCatching { StatFs(context.filesDir.absolutePath) }.getOrNull()
        val device = DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            lowRamDevice = activityManager?.isLowRamDevice ?: false,
            dataTotalBytes = stat?.let { it.blockCountLong * it.blockSizeLong },
            dataFreeBytes = stat?.let { it.availableBlocksLong * it.blockSizeLong },
            externalStorageState = Environment.getExternalStorageState(),
            automotiveFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE),
            systemFeatures = features,
            selectedFeatureChecks = selectedChecks,
        )
        val android = AndroidSnapshot(
            release = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            codename = Build.VERSION.CODENAME,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
            buildTags = Build.TAGS ?: "",
            buildType = Build.TYPE ?: "",
            javaVmVersion = System.getProperty("java.vm.version") ?: "unknown",
            locale = Locale.getDefault().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
        )
        val carApiPresent = runCatching {
            Class.forName("android.car.Car", false, context.classLoader)
            true
        }.getOrDefault(false)
        val presence = carAccess?.managerPresence()
        val automotive = AutomotiveSnapshot(
            automotiveFeature = device.automotiveFeature,
            carApiPresent = carApiPresent,
            carConnected = carAccess?.connected == true,
            carApiVersion = carAccess?.carApiVersion,
            carPlatformVersion = carAccess?.platformVersion,
            propertyServiceAvailable = presence?.propertyService == true,
            uxRestrictionServiceAvailable = presence?.uxRestrictionService == true,
            infoServiceAvailable = presence?.infoService == true,
            carFeatureEnabled = null,
            creationError = carAccess?.creationError,
            note = if (device.automotiveFeature) {
                "AAOS detected. Direct android.car access is attempted read-only; Car App Library is evaluated separately."
            } else {
                "This is not an automotive build. Vehicle-specific results are expected to be unavailable."
            },
        )

        val results = mutableListOf(
            harness.run(
                discoveryMeta(
                    "system.app",
                    com.sam2kb.lyriqprobe.core.model.ProbeModule.SYSTEM,
                    "Application build and install source",
                    "PackageManager",
                ),
            ) {
                TestOutcome(
                    status = ResultStatus.SUCCESS,
                    summary = "Probe ${app.versionName} (${app.versionCode}) installed by " +
                        "${app.installer ?: "an unknown installer"}.",
                    measurements = mapOf(
                        "package" to app.packageName,
                        "debuggable" to app.debuggable.toString(),
                        "granted_permissions" to app.grantedPermissions.size.toString(),
                    ),
                )
            },
            harness.run(
                discoveryMeta(
                    "system.build",
                    com.sam2kb.lyriqprobe.core.model.ProbeModule.SYSTEM,
                    "Android and device build",
                    "android.os.Build",
                ),
            ) {
                TestOutcome(
                    status = ResultStatus.SUCCESS,
                    summary = "Android ${android.release} (API ${android.sdkInt}) on ${device.manufacturer} ${device.model}.",
                    measurements = mapOf(
                        "security_patch" to (android.securityPatch ?: "unknown"),
                        "ram_total" to formatBytes(device.totalRamBytes),
                        "ram_available" to formatBytes(device.availableRamBytes),
                        "data_free" to formatBytes(device.dataFreeBytes),
                    ),
                )
            },
            harness.run(
                discoveryMeta(
                    "system.features",
                    com.sam2kb.lyriqprobe.core.model.ProbeModule.SYSTEM,
                    "Platform feature inventory",
                    "PackageManager.getSystemAvailableFeatures",
                ),
            ) {
                val missing = selectedChecks.filterValues { !it }.keys
                TestOutcome(
                    status = if (device.automotiveFeature) ResultStatus.AVAILABLE else ResultStatus.UNAVAILABLE,
                    summary = "${features.size} platform features reported; " +
                        "${selectedChecks.count { it.value }} of ${selectedChecks.size} selected features present.",
                    details = buildString {
                        appendLine("Present selected features:")
                        selectedChecks.filterValues { it }.keys.forEach { appendLine("- $it") }
                        if (missing.isNotEmpty()) {
                            appendLine("Missing selected features:")
                            missing.forEach { appendLine("- $it") }
                        }
                    },
                    measurements = mapOf("feature_count" to features.size.toString()),
                )
            },
            harness.run(
                discoveryMeta(
                    "system.automotive_service",
                    com.sam2kb.lyriqprobe.core.model.ProbeModule.VEHICLE,
                    "Car service connection",
                    "android.car.Car",
                ),
            ) {
                when {
                    !carApiPresent -> TestOutcome(
                        ResultStatus.UNSUPPORTED,
                        "android.car.Car is not present in this build.",
                    )
                    carAccess == null -> TestOutcome(
                        ResultStatus.UNAVAILABLE,
                        "Car connection could not be opened: ${automotive.creationError ?: "unknown reason"}",
                    )
                    carAccess.connected -> TestOutcome(
                        status = ResultStatus.AVAILABLE,
                        summary = "Car service connected; API ${automotive.carApiVersion ?: "?"}, platform ${automotive.carPlatformVersion ?: "?"}.",
                        measurements = mapOf(
                            "property_service" to automotive.propertyServiceAvailable.toString(),
                            "ux_restriction_service" to automotive.uxRestrictionServiceAvailable.toString(),
                            "info_service" to automotive.infoServiceAvailable.toString(),
                        ),
                    )
                    else -> TestOutcome(
                        ResultStatus.UNAVAILABLE,
                        "Car object exists but is not connected.",
                        exceptionClass = "CarConnection",
                        exceptionMessage = automotive.creationError,
                    )
                }
            },
        )
        return ProbeSection(SystemScan(app, device, android, automotive), results)
    }

    companion object {
        val selectedFeatureChecks = listOf(
            PackageManager.FEATURE_AUTOMOTIVE,
            PackageManager.FEATURE_CAMERA_ANY,
            PackageManager.FEATURE_CAMERA_EXTERNAL,
            PackageManager.FEATURE_CAMERA_AUTOFOCUS,
            PackageManager.FEATURE_USB_HOST,
            PackageManager.FEATURE_LOCATION,
            PackageManager.FEATURE_LOCATION_GPS,
            PackageManager.FEATURE_LOCATION_NETWORK,
            PackageManager.FEATURE_BLUETOOTH,
            PackageManager.FEATURE_BLUETOOTH_LE,
            PackageManager.FEATURE_WIFI,
            PackageManager.FEATURE_ETHERNET,
            PackageManager.FEATURE_TELEPHONY,
            PackageManager.FEATURE_SENSOR_ACCELEROMETER,
            PackageManager.FEATURE_SENSOR_GYROSCOPE,
            PackageManager.FEATURE_SENSOR_COMPASS,
            PackageManager.FEATURE_SENSOR_LIGHT,
            PackageManager.FEATURE_SENSOR_PROXIMITY,
            PackageManager.FEATURE_SENSOR_BAROMETER,
            "android.hardware.sensor.ambient_temperature",
            "android.hardware.automotive.vehicle",
        )
    }
}
