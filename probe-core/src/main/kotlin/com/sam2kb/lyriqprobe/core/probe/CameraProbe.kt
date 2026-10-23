package com.sam2kb.lyriqprobe.core.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Size
import com.sam2kb.lyriqprobe.core.model.CameraDescriptor
import com.sam2kb.lyriqprobe.core.model.CameraSnapshot
import com.sam2kb.lyriqprobe.core.model.CameraStreamConfig
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.permission.PermissionClassifier
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera2 diagnostics. Discovery never opens a camera. Opening and single-frame capture
 * are separate, explicitly requested active tests with hard timeouts and deterministic cleanup.
 *
 * Hidden factory/system cameras do not appear in getCameraIdList(). A zero count means
 * "no cameras exposed to this app", never "the vehicle has no cameras".
 */
class CameraProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val handler: Handler = Handler(Looper.getMainLooper())

    suspend fun scan(): ProbeSection<CameraSnapshot> {
        val results = mutableListOf<TestResult>()
        if (cameraManager == null) {
            val failed = harness.run(enumerationMeta) {
                TestOutcome(
                    status = ResultStatus.UNSUPPORTED,
                    summary = "CameraManager is unavailable on this build.",
                    exceptionClass = "CameraManagerUnavailable",
                )
            }
            return ProbeSection(
                snapshot = emptySnapshot("CameraManager is unavailable; no camera inventory could be collected."),
                results = listOf(failed),
            )
        }
        val ids: List<String>
        var enumerationError: Throwable? = null
        try {
            ids = cameraManager?.cameraIdList?.toList().orEmpty()
        } catch (throwable: Throwable) {
            enumerationError = throwable
            results += harness.run(enumerationMeta) { throw throwable }
            return ProbeSection(
                snapshot = emptySnapshot(
                    note = "Camera enumeration failed; no camera IDs could be queried.",
                ),
                results = results,
                fatalError = "${throwable.javaClass.simpleName}: ${throwable.message}",
            )
        }

        val descriptors = ids.map { id -> inspectCamera(id) }
        val external = descriptors.count { it.facing == "EXTERNAL" }
        val snapshot = CameraSnapshot(
            exposedCameraIds = ids,
            cameraCount = ids.size,
            externalCameraCount = external,
            cameras = descriptors,
            note = if (ids.isEmpty()) {
                "No cameras are exposed to this app through Camera2. Factory/system cameras may be installed but hidden. This is not a statement that the vehicle has no cameras."
            } else {
                "Camera IDs are visible to this app. Enumeration alone does not prove that a device can be opened or that frames can be captured."
            },
            systemCameraPermissionState = systemCameraState(),
            evsAssessment = evsAssessment(),
        )

        results += harness.run(enumerationMeta) {
            if (enumerationError != null) throw enumerationError
            TestOutcome(
                status = if (ids.isEmpty()) ResultStatus.NOT_EXPOSED else ResultStatus.AVAILABLE,
                summary = when {
                    ids.isEmpty() -> "No cameras exposed to this app through Camera2."
                    else -> "${ids.size} Camera2 camera(s) enumerated; $external external."
                },
                details = if (ids.isEmpty()) {
                    "getCameraIdList() returned an empty array. Ordinary apps cannot enumerate system-only cameras without privileged permissions."
                } else {
                    "Camera IDs: ${ids.joinToString()}\n" +
                        "Enumeration does not grant capture access. Run the explicit open/close test."
                },
                measurements = mapOf(
                    "camera_count" to ids.size.toString(),
                    "external_count" to external.toString(),
                ),
            )
        }

        descriptors.forEach { camera ->
            results += harness.run(
                discoveryMeta(
                    "camera.inspect.${camera.id}",
                    ProbeModule.CAMERAS,
                    "Camera ${camera.id} characteristics",
                    "CameraManager.getCameraCharacteristics",
                ),
            ) {
                val error = camera.inspectionError
                if (error != null) {
                    TestOutcome(
                        status = ResultStatus.ERROR,
                        summary = "Characteristics for camera ${camera.id} could not be read: $error",
                        exceptionClass = "CameraCharacteristics",
                        exceptionMessage = error,
                    )
                } else {
                    TestOutcome(
                        status = ResultStatus.AVAILABLE,
                        summary = "Camera ${camera.id}: ${camera.facing}, ${camera.hardwareLevel}, " +
                            "${camera.streamConfigurations.sumOf { it.sizes.size }} stream size(s) reported.",
                        details = cameraDetails(camera),
                        measurements = mapOf(
                            "facing" to camera.facing,
                            "hardware_level" to camera.hardwareLevel,
                            "physical_camera_ids" to camera.physicalCameraIds.joinToString(","),
                        ),
                    )
                }
            }
        }

        val systemState = systemCameraState()
        results += harness.run(
            discoveryMeta(
                "camera.system_camera",
                ProbeModule.CAMERAS,
                "System camera boundary",
                "android.permission.SYSTEM_CAMERA",
            ),
        ) {
            TestOutcome(
                status = if (systemState.startsWith("not defined")) {
                    ResultStatus.UNSUPPORTED
                } else {
                    ResultStatus.PRIVILEGED_ONLY
                },
                summary = "System camera access: $systemState. System-only cameras are not enumerated by this app.",
                details = "SYSTEM_CAMERA is a signature/privileged permission on standard AAOS. The probe reports the boundary and does not request or bypass it.",
            )
        }

        val evs = evsAssessment()
        results += harness.run(
            discoveryMeta(
                "camera.evs",
                ProbeModule.CAMERAS,
                "Automotive EVS camera boundary",
                "public Android SDK (EVS enumeration absent)",
            ),
        ) {
            TestOutcome(
                status = ResultStatus.UNSUPPORTED,
                summary = "EVS is not exposed through a public third-party SDK. No EVS enumeration or capture was attempted.",
                details = evs,
            )
        }

        return ProbeSection(snapshot, results)
    }

    suspend fun openClose(cameraId: String): TestResult = harness.run(
        activeMeta(
            id = "camera.open.$cameraId",
            module = ProbeModule.CAMERAS,
            name = "Open and close camera $cameraId",
            apiUsed = "CameraManager.openCamera",
            timeoutMs = 10_000,
        ),
    ) {
        requireCameraPermission()
        var device: CameraDevice? = null
        try {
            device = openDevice(cameraId)
            val id = device.id
            TestOutcome(
                status = ResultStatus.SUCCESS,
                summary = "Camera $id opened and was closed without a preview or recording.",
                details = "A CameraDevice.StateCallback.onOpened callback was received. No output surface was configured.",
                measurements = mapOf("camera_id" to id),
            )
        } finally {
            runCatching { device?.close() }
        }
    }

    suspend fun captureSingleFrame(cameraId: String): TestResult = harness.run(
        activeMeta(
            id = "camera.frame.$cameraId",
            module = ProbeModule.CAMERAS,
            name = "Capture one frame from camera $cameraId",
            apiUsed = "Camera2 + ImageReader",
            timeoutMs = 20_000,
        ),
    ) {
        requireCameraPermission()
        val characteristics = cameraManager?.getCameraCharacteristics(cameraId)
            ?: throw IllegalStateException("CameraManager is unavailable.")
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return@run TestOutcome(ResultStatus.UNSUPPORTED, "No stream configuration map is available.")
        val choice = chooseOutput(map)
            ?: return@run TestOutcome(
                ResultStatus.UNSUPPORTED,
                "Camera $cameraId exposes no JPEG or YUV_420_888 output size usable for an in-memory test.",
            )
        val (format, size) = choice
        val formatName = imageFormatName(format)
        val reader = ImageReader.newInstance(size.width, size.height, format, 1)
        val imageDeferred = CompletableDeferred<Image>()
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireNextImage() }.getOrNull()
            if (image == null) return@setOnImageAvailableListener
            if (!imageDeferred.complete(image)) image.close()
        }, handler)

        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        try {
            device = openDevice(cameraId)
            session = createSession(device, reader.surface)
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            builder.addTarget(reader.surface)
            session.capture(
                builder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: android.hardware.camera2.CaptureFailure,
                    ) {
                        imageDeferred.completeExceptionally(
                            IllegalStateException(
                                "Capture failed: reason=${failure.reason}, frame=${failure.frameNumber}",
                            ),
                        )
                    }
                },
                handler,
            )
            val image = withTimeout(8_000) { imageDeferred.await() }
            try {
                val payload = readPayload(image)
                if (payload.bytes <= 0) {
                    TestOutcome(
                        status = ResultStatus.ERROR,
                        summary = "Camera $cameraId produced an empty frame buffer.",
                        measurements = mapOf("format" to formatName, "bytes" to "0"),
                    )
                } else {
                    val header = if (format == ImageFormat.JPEG) {
                        payload.jpegHeaderValid
                    } else {
                        null
                    }
                    TestOutcome(
                        status = ResultStatus.SUCCESS,
                        summary = "Received one $formatName frame from camera $cameraId " +
                            "(${image.width}x${image.height}, ${payload.bytes} bytes); discarded without writing media storage.",
                        details = if (header == false) {
                            "The JPEG buffer did not begin with the expected FF D8 marker."
                        } else {
                            "Frame was held only in memory and closed immediately."
                        },
                        measurements = buildMap {
                            put("camera_id", cameraId)
                            put("format", formatName)
                            put("width", image.width.toString())
                            put("height", image.height.toString())
                            put("bytes", payload.bytes.toString())
                            header?.let { put("jpeg_header_valid", it.toString()) }
                        },
                    )
                }
            } finally {
                image.close()
            }
        } finally {
            runCatching { session?.close() }
            runCatching { device?.close() }
            reader.close()
        }
    }

    private fun requireCameraPermission() {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("android.permission.CAMERA is not granted.")
        }
    }

    private fun chooseOutput(map: android.hardware.camera2.params.StreamConfigurationMap): Pair<Int, Size>? {
        fun best(format: Int): Size? = map.getOutputSizes(format)
            ?.filter { it.width > 0 && it.height > 0 }
            ?.sortedByDescending { it.width.toLong() * it.height }
            ?.firstOrNull { it.width.toLong() * it.height <= 2_000_000L }
            ?: map.getOutputSizes(format)
                ?.filter { it.width > 0 && it.height > 0 }
                ?.minByOrNull { it.width.toLong() * it.height }
        best(ImageFormat.JPEG)?.let { return ImageFormat.JPEG to it }
        best(ImageFormat.YUV_420_888)?.let { return ImageFormat.YUV_420_888 to it }
        return null
    }

    private fun readPayload(image: Image): FramePayload {
        val planes = image.planes
        if (planes.isEmpty()) return FramePayload(0, null)
        val totalBytes = planes.sumOf { it.buffer.remaining() }
        val jpegHeader = if (image.format == ImageFormat.JPEG) {
            val buffer = planes[0].buffer.duplicate()
            if (buffer.remaining() >= 2) {
                val first = buffer.get()
                val second = buffer.get()
                first == 0xFF.toByte() && second == 0xD8.toByte()
            } else {
                false
            }
        } else {
            null
        }
        return FramePayload(totalBytes, jpegHeader)
    }

    private suspend fun openDevice(cameraId: String): CameraDevice {
        val manager = cameraManager ?: throw IllegalStateException("CameraManager is unavailable.")
        return suspendCancellableCoroutine { continuation ->
            val settled = AtomicBoolean(false)
            val deviceRef = AtomicReference<CameraDevice?>(null)
            val callback = object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    deviceRef.set(device)
                    if (settled.compareAndSet(false, true)) {
                        continuation.resume(device)
                    } else {
                        runCatching { device.close() }
                    }
                }

                override fun onDisconnected(device: CameraDevice) {
                    deviceRef.set(device)
                    if (settled.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IllegalStateException("Camera $cameraId was disconnected before it opened."),
                        )
                    }
                    runCatching { device.close() }
                }

                override fun onError(device: CameraDevice, error: Int) {
                    deviceRef.set(device)
                    if (settled.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IllegalStateException("Camera $cameraId open failed: ${cameraErrorName(error)} ($error)"),
                        )
                    }
                    runCatching { device.close() }
                }
            }
            try {
                manager.openCamera(cameraId, callback, handler)
            } catch (throwable: Throwable) {
                if (settled.compareAndSet(false, true)) {
                    continuation.resumeWithException(throwable)
                }
            }
            continuation.invokeOnCancellation {
                settled.set(true)
                runCatching { deviceRef.getAndSet(null)?.close() }
            }
        }
    }

    private suspend fun createSession(
        device: CameraDevice,
        surface: android.view.Surface,
    ): CameraCaptureSession = suspendCancellableCoroutine { continuation ->
        val settled = AtomicBoolean(false)
        val sessionRef = AtomicReference<CameraCaptureSession?>(null)
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                sessionRef.set(session)
                if (settled.compareAndSet(false, true)) {
                    continuation.resume(session)
                } else {
                    runCatching { session.close() }
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                sessionRef.set(session)
                if (settled.compareAndSet(false, true)) {
                    continuation.resumeWithException(
                        IllegalStateException("Camera capture-session configuration failed."),
                    )
                }
                runCatching { session.close() }
            }
        }
        try {
            device.createCaptureSession(listOf(surface), callback, handler)
        } catch (throwable: Throwable) {
            if (settled.compareAndSet(false, true)) {
                continuation.resumeWithException(throwable)
            }
        }
        continuation.invokeOnCancellation {
            settled.set(true)
            runCatching { sessionRef.getAndSet(null)?.close() }
        }
    }

    private fun inspectCamera(id: String): CameraDescriptor {
        return try {
            val manager = cameraManager ?: throw IllegalStateException("CameraManager unavailable.")
            val characteristics = manager.getCameraCharacteristics(id)
            val facingCode = characteristics.get(CameraCharacteristics.LENS_FACING)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.map(::capabilityName)
                .orEmpty()
            val automotiveFacing = if (android.os.Build.VERSION.SDK_INT >= 36) {
                characteristics.get(CameraCharacteristics.AUTOMOTIVE_LENS_FACING)
                    ?.map(::automotiveLensFacingName)
                    .orEmpty()
            } else {
                emptyList()
            }
            val automotiveLocation = if (android.os.Build.VERSION.SDK_INT >= 36) {
                characteristics.get(CameraCharacteristics.AUTOMOTIVE_LOCATION)
                    ?.let(::automotiveLocationName)
            } else {
                null
            }
            CameraDescriptor(
                id = id,
                facing = facingName(facingCode),
                facingCode = facingCode,
                hardwareLevel = hardwareLevelName(
                    characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL),
                ),
                infoVersion = characteristics.get(CameraCharacteristics.INFO_VERSION),
                physicalCameraIds = characteristics.physicalCameraIds.toList(),
                logicalMultiCamera = capabilities.contains("LOGICAL_MULTI_CAMERA"),
                capabilities = capabilities,
                streamConfigurations = streamConfigurations(map),
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION),
                pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.toString(),
                activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.toString(),
                flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE),
                focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                apertures = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
                automotiveLensFacing = automotiveFacing,
                automotiveLocation = automotiveLocation,
                inspectionError = null,
            )
        } catch (throwable: Throwable) {
            CameraDescriptor(
                id = id,
                facing = "UNKNOWN",
                facingCode = null,
                hardwareLevel = "UNAVAILABLE",
                infoVersion = null,
                physicalCameraIds = emptyList(),
                logicalMultiCamera = false,
                capabilities = emptyList(),
                streamConfigurations = emptyList(),
                sensorOrientation = null,
                pixelArraySize = null,
                activeArraySize = null,
                flashAvailable = null,
                focalLengths = emptyList(),
                apertures = emptyList(),
                automotiveLensFacing = emptyList(),
                automotiveLocation = null,
                inspectionError = "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}",
            )
        }
    }

    private fun streamConfigurations(
        map: android.hardware.camera2.params.StreamConfigurationMap?,
    ): List<CameraStreamConfig> {
        if (map == null) return emptyList()
        return map.outputFormats.take(8).map { format ->
            val sizes = map.getOutputSizes(format)
                .orEmpty()
                .distinct()
                .sortedByDescending { it.width.toLong() * it.height }
                .take(12)
                .map { "${it.width}x${it.height}" }
            CameraStreamConfig(imageFormatName(format), sizes)
        }
    }

    private fun emptySnapshot(note: String) = CameraSnapshot(
        exposedCameraIds = emptyList(),
        cameraCount = 0,
        externalCameraCount = 0,
        cameras = emptyList(),
        note = note,
        systemCameraPermissionState = systemCameraState(),
        evsAssessment = evsAssessment(),
    )

    private fun cameraDetails(camera: CameraDescriptor): String = buildString {
        appendLine("ID: ${camera.id}")
        appendLine("Facing: ${camera.facing} (code ${camera.facingCode})")
        appendLine("Hardware level: ${camera.hardwareLevel}")
        appendLine("Camera2 info version: ${camera.infoVersion ?: "unknown"}")
        appendLine("Physical camera IDs: ${camera.physicalCameraIds.ifEmpty { listOf("(none)") }}")
        appendLine("Logical multi-camera: ${camera.logicalMultiCamera}")
        appendLine("Capabilities: ${camera.capabilities.ifEmpty { listOf("(none reported)") }}")
        appendLine("Sensor orientation: ${camera.sensorOrientation}")
        appendLine("Pixel array: ${camera.pixelArraySize}")
        appendLine("Active array: ${camera.activeArraySize}")
        appendLine("Flash available: ${camera.flashAvailable}")
        appendLine("Focal lengths: ${camera.focalLengths}")
        appendLine("Apertures: ${camera.apertures}")
        appendLine("Automotive lens facing: ${camera.automotiveLensFacing}")
        appendLine("Automotive location: ${camera.automotiveLocation}")
        appendLine("Streams:")
        camera.streamConfigurations.forEach { stream ->
            appendLine("- ${stream.format}: ${stream.sizes.joinToString(", ")}")
        }
    }

    private fun systemCameraState(): String {
        val name = "android.permission.SYSTEM_CAMERA"
        val info = try {
            context.packageManager.getPermissionInfo(name, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: return "permission not defined on this platform"
        val protection = PermissionClassifier.classify(info.protectionLevel)
        val granted = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            "defined and granted (${protection.base})"
        } else {
            "defined but not granted (${protection.base}" +
                if (protection.flags.isEmpty()) ")" else "|${protection.flags.joinToString("|")})"
        }
    }

    private fun evsAssessment(): String = buildString {
        append("No public Android SDK exposes EVS camera enumeration or capture to ordinary apps. ")
        append("This probe does not use hidden IEvsEnumerator APIs, Binder transactions, or vendor extensions. ")
        append("EVS is therefore reported as unsupported-by-public-SDK, not as absent hardware. ")
        val carCamera = "android.car.permission.CAR_CAMERA"
        val state = try {
            val info = context.packageManager.getPermissionInfo(carCamera, 0)
            val protection = PermissionClassifier.classify(info.protectionLevel)
            "CAR_CAMERA is defined with ${protection.base}" +
                if (protection.flags.isEmpty()) " protection." else "|${protection.flags.joinToString("|")} protection."
        } catch (_: PackageManager.NameNotFoundException) {
            "CAR_CAMERA is not defined on this platform."
        }
        append(state)
    }

    private fun imageFormatName(format: Int): String = when (format) {
        ImageFormat.JPEG -> "JPEG"
        ImageFormat.YUV_420_888 -> "YUV_420_888"
        ImageFormat.PRIVATE -> "PRIVATE"
        ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
        ImageFormat.RAW10 -> "RAW10"
        ImageFormat.RAW12 -> "RAW12"
        ImageFormat.DEPTH16 -> "DEPTH16"
        else -> "FORMAT_$format"
    }

    private fun facingName(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    private fun capabilityName(capability: Int): String = when (capability) {
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI_CAMERA"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME -> "MONOCHROME"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MOTION_TRACKING -> "MOTION_TRACKING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA -> "SECURE_IMAGE_DATA"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA -> "SYSTEM_CAMERA"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING -> "OFFLINE_PROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RESOLUTION_SENSOR"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING -> "REMOSAIC_REPROCESSING"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT -> "DYNAMIC_RANGE_TEN_BIT"
        CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_STREAM_USE_CASE -> "STREAM_USE_CASE"
        else -> "CAPABILITY_$capability"
    }

    private fun automotiveLensFacingName(value: Int): String = when (value) {
        CameraMetadata.AUTOMOTIVE_LENS_FACING_EXTERIOR_FRONT -> "EXTERIOR_FRONT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_EXTERIOR_LEFT -> "EXTERIOR_LEFT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_EXTERIOR_RIGHT -> "EXTERIOR_RIGHT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_EXTERIOR_REAR -> "EXTERIOR_REAR"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_EXTERIOR_OTHER -> "EXTERIOR_OTHER"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_LEFT -> "INTERIOR_ROW1_LEFT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_CENTER -> "INTERIOR_ROW1_CENTER"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_1_RIGHT -> "INTERIOR_ROW1_RIGHT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_LEFT -> "INTERIOR_ROW2_LEFT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_CENTER -> "INTERIOR_ROW2_CENTER"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_2_RIGHT -> "INTERIOR_ROW2_RIGHT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_LEFT -> "INTERIOR_ROW3_LEFT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_CENTER -> "INTERIOR_ROW3_CENTER"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_SEAT_ROW_3_RIGHT -> "INTERIOR_ROW3_RIGHT"
        CameraMetadata.AUTOMOTIVE_LENS_FACING_INTERIOR_OTHER -> "INTERIOR_OTHER"
        else -> "AUTOMOTIVE_FACING_$value"
    }

    private fun automotiveLocationName(value: Int): String = when (value) {
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTERIOR_FRONT -> "EXTERIOR_FRONT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTERIOR_LEFT -> "EXTERIOR_LEFT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTERIOR_RIGHT -> "EXTERIOR_RIGHT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTERIOR_REAR -> "EXTERIOR_REAR"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTERIOR_OTHER -> "EXTERIOR_OTHER"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTRA_FRONT -> "EXTRA_FRONT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTRA_LEFT -> "EXTRA_LEFT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTRA_RIGHT -> "EXTRA_RIGHT"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTRA_REAR -> "EXTRA_REAR"
        CameraMetadata.AUTOMOTIVE_LOCATION_EXTRA_OTHER -> "EXTRA_OTHER"
        CameraMetadata.AUTOMOTIVE_LOCATION_INTERIOR -> "INTERIOR"
        else -> "AUTOMOTIVE_LOCATION_$value"
    }

    private fun cameraErrorName(error: Int): String = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "CAMERA_IN_USE"
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "MAX_CAMERAS_IN_USE"
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "CAMERA_DISABLED"
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "CAMERA_DEVICE"
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "CAMERA_SERVICE"
        else -> "CAMERA_ERROR_$error"
    }

    private data class FramePayload(
        val bytes: Int,
        val jpegHeaderValid: Boolean?,
    )

    private companion object {
        val enumerationMeta = discoveryMeta(
            "camera.enumerate",
            ProbeModule.CAMERAS,
            "Camera2 enumeration",
            "CameraManager.getCameraIdList",
        )
    }
}
