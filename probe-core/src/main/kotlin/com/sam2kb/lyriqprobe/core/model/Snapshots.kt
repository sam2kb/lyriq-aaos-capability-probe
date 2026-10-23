package com.sam2kb.lyriqprobe.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSnapshot(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val installer: String?,
    val minSdk: Int,
    val targetSdk: Int,
    val debuggable: Boolean,
    val grantedPermissions: List<String>,
)

@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val board: String,
    val supportedAbis: List<String>,
    val cpuCores: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowRamDevice: Boolean,
    val dataTotalBytes: Long?,
    val dataFreeBytes: Long?,
    val externalStorageState: String,
    val automotiveFeature: Boolean,
    val systemFeatures: List<String>,
    val selectedFeatureChecks: Map<String, Boolean>,
)

@Serializable
data class AndroidSnapshot(
    val release: String,
    val sdkInt: Int,
    val codename: String,
    val securityPatch: String?,
    val buildId: String,
    val fingerprint: String,
    val buildTags: String,
    val buildType: String,
    val javaVmVersion: String,
    val locale: String,
    val timeZone: String,
)

@Serializable
data class AutomotiveSnapshot(
    val automotiveFeature: Boolean,
    val carApiPresent: Boolean,
    val carConnected: Boolean,
    val carApiVersion: String?,
    val carPlatformVersion: String?,
    val propertyServiceAvailable: Boolean,
    val uxRestrictionServiceAvailable: Boolean,
    val infoServiceAvailable: Boolean,
    val carFeatureEnabled: Boolean?,
    val creationError: String?,
    val note: String,
)

@Serializable
data class PermissionEntry(
    val name: String,
    val label: String,
    val group: String?,
    val existsOnDevice: Boolean,
    val declared: Boolean,
    val granted: Boolean,
    val protectionLevel: String,
    val protectionLevelRaw: Int?,
    val protectionFlags: List<String>,
    val requestable: Boolean,
    val grantState: String,
    val purpose: String,
    val note: String? = null,
    val flagsError: String? = null,
)

@Serializable
data class PermissionSnapshot(
    val entries: List<PermissionEntry>,
    val runtimeRequestable: List<String>,
    val declaredButUnavailable: List<String>,
    val protectedPermissions: List<String>,
    val deniedRuntimePermissions: List<String>,
    val catalogNote: String,
)

@Serializable
data class CameraStreamConfig(
    val format: String,
    val sizes: List<String>,
)

@Serializable
data class CameraDescriptor(
    val id: String,
    val facing: String,
    val facingCode: Int?,
    val hardwareLevel: String,
    val infoVersion: String?,
    val physicalCameraIds: List<String>,
    val logicalMultiCamera: Boolean,
    val capabilities: List<String>,
    val streamConfigurations: List<CameraStreamConfig>,
    val sensorOrientation: Int?,
    val pixelArraySize: String?,
    val activeArraySize: String?,
    val flashAvailable: Boolean?,
    val focalLengths: List<Float>,
    val apertures: List<Float>,
    val automotiveLensFacing: List<String>,
    val automotiveLocation: String?,
    val inspectionError: String?,
)

@Serializable
data class CameraSnapshot(
    val exposedCameraIds: List<String>,
    val cameraCount: Int,
    val externalCameraCount: Int,
    val cameras: List<CameraDescriptor>,
    val note: String,
    val systemCameraPermissionState: String,
    val evsAssessment: String,
    val activeTests: List<String> = emptyList(),
)

@Serializable
data class VehiclePropertyInfo(
    val propertyId: Int,
    val name: String,
    val type: String,
    val access: String,
    val changeMode: String,
    val areaType: Int,
    val areaIds: List<Int>,
    val isGlobal: Boolean,
    val minSampleRate: Float?,
    val maxSampleRate: Float?,
    val minValue: String?,
    val maxValue: String?,
)

@Serializable
data class VehicleReading(
    val propertyId: Int,
    val name: String,
    val areaId: Int,
    val status: ResultStatus,
    val value: String?,
    val valueType: String?,
    val unit: String?,
    val propertyStatus: Int?,
    val timestampMillis: Long?,
    val summary: String,
)

@Serializable
data class VehicleSnapshot(
    val carServiceReachable: Boolean,
    val propertyServiceAvailable: Boolean,
    val propertyListError: String?,
    val propertyCount: Int,
    val properties: List<VehiclePropertyInfo>,
    val readings: List<VehicleReading>,
    val readPolicy: String,
    val privacyNote: String,
)

@Serializable
data class AndroidSensorInfo(
    val type: Int,
    val stringType: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val id: Int?,
    val resolution: Float,
    val maximumRange: Float,
    val powerMilliAmp: Float,
    val minDelayMicros: Int,
    val maxDelayMicros: Int,
    val reportingMode: String,
    val wakeUpSensor: Boolean,
    val dynamicSensor: Boolean,
    val additionalInfoSupported: Boolean,
    val fifoMaxEventCount: Int,
    val highestDirectReportRateLevel: Int,
)

@Serializable
data class SensorSample(
    val sensorType: Int,
    val sensorName: String,
    val durationMs: Long,
    val eventCount: Int,
    val minValues: List<Float>,
    val maxValues: List<Float>,
    val meanValues: List<Float>,
    val lastValues: List<Float>,
    val timestampNanos: Long?,
    val summary: String,
)

@Serializable
data class SensorSnapshot(
    val sensors: List<AndroidSensorInfo>,
    val note: String,
    val lastSample: SensorSample? = null,
)

@Serializable
data class StorageVolumeInfo(
    val description: String,
    val state: String,
    val mounted: Boolean,
    val removable: Boolean,
    val emulated: Boolean,
    val primary: Boolean,
    val mediaStoreVolumeName: String?,
    val path: String?,
    val totalBytes: Long?,
    val availableBytes: Long?,
    val accessError: String?,
)

@Serializable
data class AppExternalDirInfo(
    val label: String,
    val path: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val removable: Boolean,
    val emulated: Boolean,
    val totalBytes: Long?,
    val availableBytes: Long?,
)

@Serializable
data class SelectedTreeInfo(
    val displayName: String,
    val canRead: Boolean,
    val canWrite: Boolean,
    val readPersisted: Boolean,
    val writePersisted: Boolean,
    val childCount: Int?,
    val error: String?,
)

@Serializable
data class StorageStep(
    val name: String,
    val success: Boolean,
    val detail: String,
)

@Serializable
data class StorageTestRecord(
    val displayName: String,
    val success: Boolean,
    val steps: List<StorageStep>,
    val startedAt: String,
    val completedAt: String,
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
)

@Serializable
data class StorageSnapshot(
    val volumes: List<StorageVolumeInfo>,
    val appExternalDirs: List<AppExternalDirInfo>,
    val externalStorageState: String,
    val selectedTree: SelectedTreeInfo?,
    val storageAccessFrameworkNote: String,
    val testRecord: StorageTestRecord? = null,
)

@Serializable
data class UsbDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val manufacturerName: String?,
    val productName: String?,
)

@Serializable
data class UsbInventory(
    val hostFeature: Boolean,
    val managerAvailable: Boolean,
    val deviceCount: Int,
    val devices: List<UsbDeviceInfo>,
    val error: String?,
)

@Serializable
data class BluetoothInfo(
    val feature: Boolean,
    val adapterAvailable: Boolean,
    val enabled: Boolean?,
    val state: String?,
    val permissionState: String,
    val error: String?,
)

@Serializable
data class WifiInfo(
    val feature: Boolean,
    val managerAvailable: Boolean,
    val enabled: Boolean?,
    val error: String?,
)

@Serializable
data class NetworkSummary(
    val transport: List<String>,
    val capabilities: List<String>,
    val metered: Boolean?,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
)

@Serializable
data class NetworkInventory(
    val available: Boolean,
    val networkCount: Int,
    val networks: List<NetworkSummary>,
    val error: String?,
)

@Serializable
data class LocationProviderInfo(
    val name: String,
    val enabled: Boolean,
    val accuracy: Int,
    val powerUsage: Int,
    val requiresNetwork: Boolean,
    val requiresSatellite: Boolean,
    val requiresCell: Boolean,
    val hasMonetaryCost: Boolean,
    val supportsAltitude: Boolean,
    val supportsSpeed: Boolean,
    val supportsBearing: Boolean,
)

@Serializable
data class LocationInventory(
    val featureGps: Boolean,
    val locationEnabled: Boolean?,
    val providers: List<LocationProviderInfo>,
    val error: String?,
)

@Serializable
data class DisplayInfo(
    val displayId: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRate: Float,
    val rotation: Int,
    val hdrTypes: List<String>,
)

@Serializable
data class AudioDeviceInfo(
    val type: String,
    val name: String,
    val sink: Boolean,
    val source: Boolean,
    val channelCounts: List<Int>,
    val sampleRates: List<Int>,
)

@Serializable
data class AudioInventory(
    val outputDevices: List<AudioDeviceInfo>,
    val inputDevices: List<AudioDeviceInfo>,
    val error: String?,
)

@Serializable
data class ConnectivitySnapshot(
    val bluetooth: BluetoothInfo,
    val wifi: WifiInfo,
    val network: NetworkInventory,
    val location: LocationInventory,
    val usb: UsbInventory,
    val displays: List<DisplayInfo>,
    val audio: AudioInventory,
    val privacyNote: String,
)

@Serializable
data class MediaCodecInfo(
    val name: String,
    val mimeTypes: List<String>,
    val hardwareAccelerated: Boolean?,
    val softwareOnly: Boolean?,
    val maxSupportedInstances: Int?,
)

@Serializable
data class AudioPathSnapshot(
    val nativeOutputSampleRate: Int?,
    val framesPerBuffer: Int?,
    val minBufferBytes44100Stereo: Int?,
    val minBufferBytes48000Stereo: Int?,
    val directPlaybackSupported: Map<String, Boolean>,
    val outputDeviceSummary: List<String>,
)

@Serializable
data class ForegroundServiceTestResult(
    val attempted: Boolean,
    val started: Boolean,
    val stopped: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val notificationChannelCreated: Boolean,
    val error: String?,
)

@Serializable
data class SilentAudioTestResult(
    val attempted: Boolean,
    val succeeded: Boolean,
    val sampleRate: Int,
    val channelCount: Int,
    val bufferBytes: Int,
    val playState: String?,
    val error: String?,
)

@Serializable
data class MediaSnapshot(
    val audioDecoders: List<MediaCodecInfo>,
    val mimeSupport: Map<String, Boolean>,
    val audioPath: AudioPathSnapshot,
    val mediaSessionApiPresent: Boolean,
    val mediaBrowserServicePresent: Boolean,
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val foregroundServicePermissions: Map<String, Boolean>,
    val lastForegroundServiceTest: ForegroundServiceTestResult? = null,
    val lastSilentAudioTest: SilentAudioTestResult? = null,
    val note: String,
)

@Serializable
data class LmsEndpointResult(
    val label: String,
    val port: Int,
    val dnsResolved: Boolean,
    val connected: Boolean,
    val latencyMs: Long?,
    val errorClass: String?,
    val errorMessage: String?,
)

@Serializable
data class LmsNetworkSnapshot(
    val internetPermissionGranted: Boolean,
    val localNetworkPermissionDefined: Boolean,
    val localNetworkPermissionGranted: Boolean,
    val nearbyWifiPermissionDefined: Boolean,
    val cleartextTrafficPermitted: Boolean,
    val endpointKind: String?,
    val lastTest: List<LmsEndpointResult> = emptyList(),
    val note: String,
)

@Serializable
data class DrivingStateSnapshot(
    val available: Boolean,
    val requiresDistractionOptimization: Boolean?,
    val activeRestrictions: Int?,
    val restrictionNames: List<String>,
    val maxContentDepth: Int?,
    val maxCumulativeContentItems: Int?,
    val maxRestrictedStringLength: Int?,
    val error: String?,
    val note: String,
)

@Serializable
data class CarAppLibrarySnapshot(
    val integrated: Boolean,
    val status: ResultStatus,
    val reason: String,
    val libraryVersion: String?,
    val observedAt: String,
)

@Serializable
data class ReportSummary(
    val totalTests: Int,
    val statusCounts: Map<String, Int>,
    val cameraCount: Int,
    val vehiclePropertyCount: Int,
    val sensorCount: Int,
    val storageVolumeCount: Int,
    val keyFindings: List<String>,
    val humanReadable: String,
)

@Serializable
data class ProbeReport(
    @kotlinx.serialization.SerialName("schema_version")
    val schemaVersion: String,
    @kotlinx.serialization.SerialName("app_version")
    val appVersion: String,
    val timestamp: String,
    val app: AppSnapshot?,
    val device: DeviceSnapshot?,
    val android: AndroidSnapshot?,
    val automotive: AutomotiveSnapshot?,
    val permissions: PermissionSnapshot?,
    val cameras: CameraSnapshot?,
    @kotlinx.serialization.SerialName("vehicle_properties")
    val vehicleProperties: VehicleSnapshot?,
    @kotlinx.serialization.SerialName("android_sensors")
    val androidSensors: SensorSnapshot?,
    val storage: StorageSnapshot?,
    val connectivity: ConnectivitySnapshot?,
    val media: MediaSnapshot?,
    @kotlinx.serialization.SerialName("lms_network")
    val lmsNetwork: LmsNetworkSnapshot?,
    @kotlinx.serialization.SerialName("driving_state")
    val drivingState: DrivingStateSnapshot?,
    @kotlinx.serialization.SerialName("car_app_library")
    val carAppLibrary: CarAppLibrarySnapshot?,
    val summary: ReportSummary,
    @kotlinx.serialization.SerialName("test_results")
    val testResults: List<TestResult>,
    val errors: List<ProbeError>,
)

data class ProbeData(
    val app: AppSnapshot? = null,
    val device: DeviceSnapshot? = null,
    val android: AndroidSnapshot? = null,
    val automotive: AutomotiveSnapshot? = null,
    val permissions: PermissionSnapshot? = null,
    val cameras: CameraSnapshot? = null,
    val vehicle: VehicleSnapshot? = null,
    val sensors: SensorSnapshot? = null,
    val storage: StorageSnapshot? = null,
    val connectivity: ConnectivitySnapshot? = null,
    val media: MediaSnapshot? = null,
    val lmsNetwork: LmsNetworkSnapshot? = null,
    val driving: DrivingStateSnapshot? = null,
    val carAppLibrary: CarAppLibrarySnapshot? = null,
    val results: List<TestResult> = emptyList(),
    val errors: List<ProbeError> = emptyList(),
)
