package com.sam2kb.lyriqprobe.core.probe

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.usb.UsbManager
import android.location.LocationManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.sam2kb.lyriqprobe.core.model.AudioDeviceInfo as AudioDeviceSnapshot
import com.sam2kb.lyriqprobe.core.model.AudioInventory
import com.sam2kb.lyriqprobe.core.model.BluetoothInfo
import com.sam2kb.lyriqprobe.core.model.ConnectivitySnapshot
import com.sam2kb.lyriqprobe.core.model.DisplayInfo
import com.sam2kb.lyriqprobe.core.model.LocationInventory
import com.sam2kb.lyriqprobe.core.model.LocationProviderInfo
import com.sam2kb.lyriqprobe.core.model.NetworkInventory
import com.sam2kb.lyriqprobe.core.model.NetworkSummary
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.UsbDeviceInfo
import com.sam2kb.lyriqprobe.core.model.UsbInventory
import com.sam2kb.lyriqprobe.core.model.WifiInfo
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectivityProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val packageManager = context.packageManager

    suspend fun scan(): ProbeSection<ConnectivitySnapshot> = withContext(Dispatchers.IO) {
        val bluetooth = readBluetooth()
        val wifi = readWifi()
        val network = readNetwork()
        val location = readLocation()
        val usb = readUsb()
        val displays = readDisplays()
        val audio = readAudio()
        val snapshot = ConnectivitySnapshot(
            bluetooth = bluetooth,
            wifi = wifi,
            network = network,
            location = location,
            usb = usb,
            displays = displays,
            audio = audio,
            privacyNote = "No SSIDs, BSSIDs, IP addresses, MAC addresses, USB serial numbers, precise location, " +
                "or paired-device lists are collected.",
        )
        val results = mutableListOf<com.sam2kb.lyriqprobe.core.model.TestResult>()
        results += harness.run(
            discoveryMeta(
                "connectivity.bluetooth",
                ProbeModule.CONNECTIVITY,
                "Bluetooth adapter inventory",
                "BluetoothManager",
            ),
        ) {
            TestOutcome(
                status = when {
                    !bluetooth.feature -> ResultStatus.UNSUPPORTED
                    !bluetooth.adapterAvailable -> ResultStatus.NOT_EXPOSED
                    bluetooth.error != null -> ResultStatus.DENIED
                    else -> ResultStatus.AVAILABLE
                },
                summary = when {
                    !bluetooth.feature -> "Bluetooth feature is not declared by the platform."
                    !bluetooth.adapterAvailable -> "Bluetooth adapter is not exposed."
                    bluetooth.error != null -> "Bluetooth state denied: ${bluetooth.error}"
                    else -> "Bluetooth adapter present and ${if (bluetooth.enabled == true) "enabled" else "disabled"}."
                },
                details = "Adapter state: ${bluetooth.state ?: "unknown"}\nPermission: ${bluetooth.permissionState}",
                measurements = mapOf("enabled" to (bluetooth.enabled?.toString() ?: "unknown")),
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.wifi",
                ProbeModule.CONNECTIVITY,
                "Wi-Fi inventory",
                "WifiManager",
            ),
        ) {
            TestOutcome(
                status = when {
                    !wifi.feature -> ResultStatus.UNSUPPORTED
                    !wifi.managerAvailable -> ResultStatus.NOT_EXPOSED
                    wifi.error != null -> ResultStatus.DENIED
                    else -> ResultStatus.AVAILABLE
                },
                summary = if (wifi.error != null) {
                    "Wi-Fi enabled state unavailable: ${wifi.error}"
                } else {
                    "Wi-Fi ${if (wifi.enabled == true) "enabled" else "disabled or unknown"}; no scan was performed."
                },
                details = "No SSID, BSSID, IP, or MAC address is collected.",
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.network",
                ProbeModule.CONNECTIVITY,
                "Network transports",
                "ConnectivityManager",
            ),
        ) {
            TestOutcome(
                status = if (network.error != null) ResultStatus.DENIED else ResultStatus.AVAILABLE,
                summary = if (network.error != null) {
                    "Network inventory unavailable: ${network.error}"
                } else {
                    "${network.networkCount} network(s) reported; transport/capability metadata only."
                },
                details = network.networks.joinToString("\n") {
                    "transport=${it.transport}, capabilities=${it.capabilities}, metered=${it.metered}"
                },
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.location",
                ProbeModule.CONNECTIVITY,
                "Location provider and GNSS availability",
                "LocationManager",
            ),
        ) {
            TestOutcome(
                status = if (location.error != null) ResultStatus.ERROR else ResultStatus.AVAILABLE,
                summary = if (location.error != null) {
                    "Location provider inventory unavailable: ${location.error}"
                } else {
                    "${location.providers.count { it.enabled }} enabled provider(s); GNSS feature=${location.featureGps}. " +
                        "No location fix was requested."
                },
                details = location.providers.joinToString("\n") {
                    "${it.name}: enabled=${it.enabled}, accuracy=${it.accuracy}, power=${it.powerUsage}, " +
                        "satellite=${it.requiresSatellite}, network=${it.requiresNetwork}"
                },
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.usb",
                ProbeModule.CONNECTIVITY,
                "USB host inventory",
                "UsbManager.getDeviceList",
            ),
        ) {
            TestOutcome(
                status = when {
                    !usb.hostFeature -> ResultStatus.UNSUPPORTED
                    !usb.managerAvailable -> ResultStatus.UNSUPPORTED
                    usb.error != null -> ResultStatus.DENIED
                    usb.deviceCount == 0 -> ResultStatus.AVAILABLE
                    else -> ResultStatus.AVAILABLE
                },
                summary = when {
                    !usb.hostFeature -> "USB host feature is not declared."
                    !usb.managerAvailable -> "UsbManager is unavailable."
                    usb.error != null -> "USB inventory unavailable: ${usb.error}"
                    usb.deviceCount == 0 -> "USB host available; no UsbManager devices currently visible. " +
                        "A mounted mass-storage volume can be absent from this list."
                    else -> "${usb.deviceCount} USB device(s) visible through UsbManager."
                },
                details = usb.devices.joinToString("\n") {
                    "vid=0x${it.vendorId.toString(16)} pid=0x${it.productId.toString(16)} " +
                        "class=${it.deviceClass} interfaces=${it.interfaceCount} product=${it.productName ?: "?"}"
                },
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.displays",
                ProbeModule.CONNECTIVITY,
                "Display inventory",
                "DisplayManager",
            ),
        ) {
            TestOutcome(
                status = if (displays.isEmpty()) ResultStatus.NOT_EXPOSED else ResultStatus.AVAILABLE,
                summary = "${displays.size} display(s) reported.",
                details = displays.joinToString("\n") {
                    "display ${it.displayId} (${it.name}): ${it.widthPx}x${it.heightPx} @ ${it.densityDpi} dpi, " +
                        "${it.refreshRate} Hz, rotation ${it.rotation}, HDR=${it.hdrTypes}"
                },
            )
        }
        results += harness.run(
            discoveryMeta(
                "connectivity.audio",
                ProbeModule.CONNECTIVITY,
                "Audio device inventory",
                "AudioManager.getDevices",
            ),
        ) {
            TestOutcome(
                status = if (audio.error != null) {
                    ResultStatus.ERROR
                } else if (audio.outputDevices.isEmpty() && audio.inputDevices.isEmpty()) {
                    ResultStatus.NOT_EXPOSED
                } else {
                    ResultStatus.AVAILABLE
                },
                summary = "${audio.outputDevices.size} output and ${audio.inputDevices.size} input audio device(s) reported.",
                details = if (audio.error != null) {
                    "Audio inventory partially unavailable: ${audio.error}"
                } else {
                    (audio.outputDevices + audio.inputDevices).joinToString("\n") {
                        "${it.name}: type=${it.type}, sink=${it.sink}, source=${it.source}, rates=${it.sampleRates}"
                    }
                },
            )
        }
        ProbeSection(snapshot, results)
    }

    private fun readBluetooth(): BluetoothInfo {
        val feature = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            return BluetoothInfo(feature, false, null, null, "unavailable", null)
        }
        val connectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return try {
            BluetoothInfo(
                feature = feature,
                adapterAvailable = true,
                enabled = adapter.isEnabled,
                state = bluetoothStateName(adapter.state),
                permissionState = if (connectPermission) "granted/not required" else "BLUETOOTH_CONNECT denied",
                error = if (connectPermission) null else "BLUETOOTH_CONNECT is required on Android 12+.",
            )
        } catch (throwable: SecurityException) {
            BluetoothInfo(
                feature = feature,
                adapterAvailable = true,
                enabled = null,
                state = null,
                permissionState = "denied",
                error = "${throwable.javaClass.simpleName}: ${throwable.message}",
            )
        }
    }

    private fun readWifi(): WifiInfo {
        val feature = packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
        return try {
            val manager = context.getSystemService(android.net.wifi.WifiManager::class.java)
            WifiInfo(
                feature = feature,
                managerAvailable = manager != null,
                enabled = manager?.isWifiEnabled,
                error = null,
            )
        } catch (throwable: Throwable) {
            WifiInfo(feature, false, null, "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun readNetwork(): NetworkInventory {
        return try {
            val manager = context.getSystemService(ConnectivityManager::class.java)
                ?: return NetworkInventory(false, 0, emptyList(), "ConnectivityManager unavailable.")
            val summaries = manager.allNetworks.mapNotNull { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
                NetworkSummary(
                    transport = transports(capabilities),
                    capabilities = capabilitiesList(capabilities),
                    metered = manager.isActiveNetworkMetered.takeIf { manager.activeNetwork == network },
                    downstreamKbps = capabilities.linkDownstreamBandwidthKbps.takeIf { it > 0 },
                    upstreamKbps = capabilities.linkUpstreamBandwidthKbps.takeIf { it > 0 },
                )
            }
            NetworkInventory(true, summaries.size, summaries, null)
        } catch (throwable: Throwable) {
            NetworkInventory(false, 0, emptyList(), "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun readLocation(): LocationInventory {
        return try {
            val manager = context.getSystemService(LocationManager::class.java)
                ?: return LocationInventory(false, null, emptyList(), "LocationManager unavailable.")
            val providers = manager.allProviders.mapNotNull { name ->
                val provider = manager.getProvider(name) ?: return@mapNotNull null
                LocationProviderInfo(
                    name = name,
                    enabled = manager.isProviderEnabled(name),
                    accuracy = provider.accuracy,
                    powerUsage = provider.powerRequirement,
                    requiresNetwork = provider.requiresNetwork(),
                    requiresSatellite = provider.requiresSatellite(),
                    requiresCell = provider.requiresCell(),
                    hasMonetaryCost = provider.hasMonetaryCost(),
                    supportsAltitude = provider.supportsAltitude(),
                    supportsSpeed = provider.supportsSpeed(),
                    supportsBearing = provider.supportsBearing(),
                )
            }
            LocationInventory(
                featureGps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
                locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    manager.isLocationEnabled
                } else {
                    null
                },
                providers = providers,
                error = null,
            )
        } catch (throwable: Throwable) {
            LocationInventory(false, null, emptyList(), "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun readUsb(): UsbInventory {
        val feature = packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        return try {
            val manager = context.getSystemService(UsbManager::class.java)
                ?: return UsbInventory(feature, false, 0, emptyList(), "UsbManager unavailable.")
            val devices = manager.deviceList.values.map {
                UsbDeviceInfo(
                    vendorId = it.vendorId,
                    productId = it.productId,
                    deviceClass = it.deviceClass,
                    deviceSubclass = it.deviceSubclass,
                    deviceProtocol = it.deviceProtocol,
                    interfaceCount = it.interfaceCount,
                    manufacturerName = runCatching { it.manufacturerName }.getOrNull(),
                    productName = runCatching { it.productName }.getOrNull(),
                )
            }
            UsbInventory(feature, true, devices.size, devices, null)
        } catch (throwable: Throwable) {
            UsbInventory(feature, true, 0, emptyList(), "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun readDisplays(): List<DisplayInfo> {
        return try {
            val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
            manager.displays.mapNotNull { display ->
                val mode = display.mode ?: return@mapNotNull null
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                display.getRealMetrics(metrics)
                DisplayInfo(
                    displayId = display.displayId,
                    name = display.name ?: "display",
                    widthPx = mode.physicalWidth,
                    heightPx = mode.physicalHeight,
                    densityDpi = metrics.densityDpi,
                    refreshRate = mode.refreshRate,
                    rotation = display.rotation,
                    hdrTypes = display.hdrCapabilities?.supportedHdrTypes?.map(::hdrTypeName).orEmpty(),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun readAudio(): AudioInventory {
        return try {
            val manager = context.getSystemService(AudioManager::class.java)
                ?: return AudioInventory(emptyList(), emptyList(), "AudioManager unavailable.")
            val devices = manager.getDevices(AudioManager.GET_DEVICES_ALL)
            AudioInventory(
                outputDevices = devices.filter { it.isSink }.map(::audioInfo),
                inputDevices = devices.filter { it.isSource }.map(::audioInfo),
                error = null,
            )
        } catch (throwable: Throwable) {
            AudioInventory(emptyList(), emptyList(), "${throwable.javaClass.simpleName}: ${throwable.message}")
        }
    }

    private fun audioInfo(device: AudioDeviceInfo): AudioDeviceSnapshot = AudioDeviceSnapshot(
        type = audioTypeName(device.type),
        name = device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "unnamed",
        sink = device.isSink,
        source = device.isSource,
        channelCounts = device.channelCounts.toList(),
        sampleRates = device.sampleRates.toList(),
    )

    private fun transports(capabilities: NetworkCapabilities): List<String> = buildList {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI_AWARE)) add("WIFI_AWARE")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN)) add("LOWPAN")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB)) add("USB")
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE)) add("SATELLITE")
    }

    private fun capabilitiesList(capabilities: NetworkCapabilities): List<String> = buildList {
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) add("INTERNET")
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("VALIDATED")
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) add("NOT_METERED")
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)) add("NOT_ROAMING")
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) add("NOT_VPN")
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) add("CAPTIVE_PORTAL")
    }

    private fun bluetoothStateName(state: Int): String = when (state) {
        BluetoothAdapter.STATE_OFF -> "OFF"
        BluetoothAdapter.STATE_TURNING_ON -> "TURNING_ON"
        BluetoothAdapter.STATE_ON -> "ON"
        BluetoothAdapter.STATE_TURNING_OFF -> "TURNING_OFF"
        else -> "UNKNOWN_$state"
    }

    private fun hdrTypeName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "DOLBY_VISION"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10_PLUS"
        else -> "HDR_$type"
    }

    private fun audioTypeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "MIC"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BT_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_TELEPHONY -> "TELEPHONY"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        else -> "TYPE_$type"
    }
}
