package com.sam2kb.lyriqprobe.core.probe

import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import com.sam2kb.lyriqprobe.core.car.CarAccess
import com.sam2kb.lyriqprobe.core.car.readableAccessLabel
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.model.VehiclePropertyInfo
import com.sam2kb.lyriqprobe.core.model.VehicleReading
import com.sam2kb.lyriqprobe.core.model.VehicleSnapshot
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta

data class VehicleReadResult(
    val readings: List<VehicleReading>,
    val result: TestResult,
)

/**
 * Read-only CarPropertyManager inspection.
 *
 * The class intentionally has no setProperty call and no reference to any actuator.
 * INFO_VIN is never read; it may appear as metadata in getPropertyList().
 */
class VehicleProbe(
    private val harness: TestHarness = TestHarness(),
) {
    suspend fun scanProperties(carAccess: CarAccess?): ProbeSection<VehicleSnapshot> {
        if (carAccess == null || !carAccess.connected) {
            val reason = carAccess?.creationError ?: "No Car connection was established."
            return ProbeSection(
                snapshot = emptyVehicleSnapshot(
                    reachable = false,
                    propertyListError = reason,
                ),
                results = listOf(
                    harness.run(
                        discoveryMeta(
                            "vehicle.property_list",
                            ProbeModule.VEHICLE,
                            "Vehicle property list",
                            "CarPropertyManager.getPropertyList",
                        ),
                    ) {
                        TestOutcome(
                            status = ResultStatus.UNAVAILABLE,
                            summary = "Vehicle property inventory unavailable: $reason",
                            exceptionClass = "CarConnection",
                            exceptionMessage = reason,
                        )
                    },
                    harness.run(
                        discoveryMeta(
                            "vehicle.values",
                            ProbeModule.VEHICLE,
                            "Vehicle value reading",
                            "CarPropertyManager.getProperty",
                        ),
                    ) {
                        TestOutcome(
                            ResultStatus.NOT_TESTED,
                            "No car connection; values were not read.",
                        )
                    },
                ),
            )
        }

        val manager = carAccess.propertyManager()
        if (manager == null) {
            return ProbeSection(
                snapshot = emptyVehicleSnapshot(
                    reachable = true,
                    propertyListError = "CarPropertyManager is unavailable.",
                ),
                results = listOf(
                    harness.run(
                        discoveryMeta(
                            "vehicle.property_list",
                            ProbeModule.VEHICLE,
                            "Vehicle property list",
                            "CarPropertyManager.getPropertyList",
                        ),
                    ) {
                        TestOutcome(
                            ResultStatus.UNSUPPORTED,
                            "Car is connected but CarPropertyManager is unavailable.",
                        )
                    },
                ),
            )
        }

        var error: Throwable? = null
        val configs: List<CarPropertyConfig<*>> = try {
            manager.propertyList
        } catch (throwable: Throwable) {
            error = throwable
            emptyList()
        }
        val properties = configs.map(::toPropertyInfo).sortedBy { it.propertyId }
        val snapshot = VehicleSnapshot(
            carServiceReachable = true,
            propertyServiceAvailable = true,
            propertyListError = error?.let { "${it.javaClass.simpleName}: ${it.message ?: "no message"}" },
            propertyCount = properties.size,
            properties = properties,
            readings = emptyList(),
            readPolicy = "Read-only. Only an allowlist of informational/energy values is read on explicit action. " +
                "No vehicle property writes or actuator commands exist in this app.",
            privacyNote = "INFO_VIN may be listed as accessible metadata but its value is never read or exported. " +
                "No account, location, or driver identity is collected.",
        )
        val results = mutableListOf<TestResult>()
        results += harness.run(
            discoveryMeta(
                "vehicle.property_list",
                ProbeModule.VEHICLE,
                "Vehicle property list",
                "CarPropertyManager.getPropertyList",
            ),
        ) {
            if (error != null) {
                TestOutcome(
                    status = if (error is SecurityException) ResultStatus.DENIED else ResultStatus.ERROR,
                    summary = "getPropertyList() failed: ${error?.javaClass?.simpleName}: ${error?.message}",
                    details = error?.stackTraceToString(),
                    exceptionClass = error?.javaClass?.name,
                    exceptionMessage = error?.message,
                )
            } else {
                TestOutcome(
                    status = if (properties.isEmpty()) ResultStatus.NOT_EXPOSED else ResultStatus.AVAILABLE,
                    summary = if (properties.isEmpty()) {
                        "No vehicle properties are accessible to this app."
                    } else {
                        "${properties.size} vehicle propert${if (properties.size == 1) "y" else "ies"} are accessible read-only metadata."
                    },
                    details = buildString {
                        appendLine("getPropertyList() returns only properties the caller is allowed to see.")
                        appendLine("Configuration entries do not prove that values can be read; values require the explicit test.")
                        appendLine()
                        properties.forEach {
                            appendLine("- ${it.name} (0x${it.propertyId.toString(16)}): ${it.type}, ${it.access}, ${it.changeMode}")
                        }
                    },
                    measurements = mapOf(
                        "property_count" to properties.size.toString(),
                        "includes_vin_metadata" to properties.any { it.name == "INFO_VIN" }.toString(),
                    ),
                )
            }
        }
        results += harness.run(
            discoveryMeta(
                "vehicle.values",
                ProbeModule.VEHICLE,
                "Vehicle value reading",
                "CarPropertyManager.getProperty",
            ),
        ) {
            TestOutcome(
                ResultStatus.NOT_TESTED,
                "Value reads are an active test; property metadata was collected without reading values.",
            )
        }
        return ProbeSection(snapshot, results)
    }

    suspend fun readValues(carAccess: CarAccess?): VehicleReadResult {
        var collectedReadings: List<VehicleReading> = emptyList()
        val result = harness.run(
            activeMeta(
                id = "vehicle.read_values",
                module = ProbeModule.VEHICLE,
                name = "Read allowlisted vehicle values",
                apiUsed = "CarPropertyManager.getProperty",
                timeoutMs = 12_000,
            ),
        ) {
            if (carAccess == null || !carAccess.connected) {
                return@run TestOutcome(
                    status = ResultStatus.UNAVAILABLE,
                    summary = "No car connection is available.",
                    exceptionClass = "CarConnection",
                    exceptionMessage = carAccess?.creationError,
                )
            }
            val manager = carAccess.propertyManager()
                ?: return@run TestOutcome(
                    status = ResultStatus.UNSUPPORTED,
                    summary = "CarPropertyManager is unavailable.",
                )
            val configs = try {
                manager.propertyList
            } catch (throwable: Throwable) {
                return@run TestOutcome(
                    status = if (throwable is SecurityException) ResultStatus.DENIED else ResultStatus.ERROR,
                    summary = "Could not enumerate properties before reading: " +
                        "${throwable.javaClass.simpleName}: ${throwable.message}",
                    exceptionClass = throwable.javaClass.name,
                    exceptionMessage = throwable.message,
                )
            }
            val byId = configs.associateBy { it.propertyId }
            val readings = allowedReadProperties.map { id ->
                readOne(manager, byId[id], id)
            }
            collectedReadings = readings
            val successes = readings.count { it.status == ResultStatus.SUCCESS }
            val status = when {
                successes == readings.size -> ResultStatus.SUCCESS
                successes > 0 -> ResultStatus.AVAILABLE
                readings.all { it.status == ResultStatus.DENIED } -> ResultStatus.DENIED
                readings.all { it.status == ResultStatus.NOT_EXPOSED } -> ResultStatus.NOT_EXPOSED
                else -> ResultStatus.UNAVAILABLE
            }
            TestOutcome(
                status = status,
                summary = "$successes of ${readings.size} allowlisted vehicle values were read. " +
                    "VIN was not requested; no write was attempted.",
                details = readings.joinToString("\n\n") {
                    "${it.name} (area ${it.areaId}, ${it.status}): ${it.value ?: "no value"}" +
                        if (it.unit == null) "" else " ${it.unit}"
                },
                measurements = mapOf(
                    "values_read" to successes.toString(),
                    "requested" to readings.size.toString(),
                ),
            )
        }
        return VehicleReadResult(readings = collectedReadings, result = result)
    }

    private fun readOne(
        manager: CarPropertyManager,
        config: CarPropertyConfig<*>?,
        id: Int,
    ): VehicleReading {
        val name = propertyName(id)
        if (config == null) {
            return VehicleReading(
                propertyId = id,
                name = name,
                areaId = 0,
                status = ResultStatus.NOT_EXPOSED,
                value = null,
                valueType = null,
                unit = propertyUnit(id),
                propertyStatus = null,
                timestampMillis = null,
                summary = "Not present in getPropertyList(); this app cannot see the property.",
            )
        }
        if (config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE ||
            config.access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE
        ) {
            return VehicleReading(
                propertyId = id,
                name = name,
                areaId = 0,
                status = ResultStatus.UNSUPPORTED,
                value = null,
                valueType = config.propertyType.simpleName,
                unit = propertyUnit(id),
                propertyStatus = null,
                timestampMillis = null,
                summary = "Property is listed but is not readable (access=${config.readableAccessLabel()}).",
            )
        }
        val areaId = if (config.isGlobalProperty) 0 else config.areaIds.firstOrNull() ?: 0
        return try {
            @Suppress("UNCHECKED_CAST")
            val propertyValue = manager.getProperty(
                config.propertyType as Class<Any>,
                id,
                areaId,
            ) as CarPropertyValue<*>
            val valueText = formatValue(propertyValue.value)
            val available = propertyValue.status == CarPropertyValue.STATUS_AVAILABLE
            VehicleReading(
                propertyId = id,
                name = name,
                areaId = areaId,
                status = if (available) ResultStatus.SUCCESS else ResultStatus.UNAVAILABLE,
                value = valueText,
                valueType = propertyValue.value?.javaClass?.simpleName ?: config.propertyType.simpleName,
                unit = propertyUnit(id),
                propertyStatus = propertyValue.status,
                timestampMillis = propertyValue.timestamp,
                summary = if (available) {
                    "Read from area $areaId."
                } else {
                    "Property status ${propertyValue.status} means the value is not available."
                },
            )
        } catch (throwable: Throwable) {
            val status = when {
                throwable is SecurityException -> ResultStatus.DENIED
                throwable is IllegalStateException -> ResultStatus.UNAVAILABLE
                else -> ResultStatus.ERROR
            }
            VehicleReading(
                propertyId = id,
                name = name,
                areaId = areaId,
                status = status,
                value = null,
                valueType = config.propertyType.simpleName,
                unit = propertyUnit(id),
                propertyStatus = null,
                timestampMillis = null,
                summary = "${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}",
            )
        }
    }

    private fun toPropertyInfo(config: CarPropertyConfig<*>): VehiclePropertyInfo = VehiclePropertyInfo(
        propertyId = config.propertyId,
        name = propertyName(config.propertyId),
        type = config.propertyType.simpleName,
        access = config.readableAccessLabel(),
        changeMode = changeModeName(config.changeMode),
        areaType = config.areaType,
        areaIds = config.areaIds.toList(),
        isGlobal = config.isGlobalProperty,
        minSampleRate = config.minSampleRate,
        maxSampleRate = config.maxSampleRate,
        minValue = config.minValue?.let(::formatValue),
        maxValue = config.maxValue?.let(::formatValue),
    )

    private fun emptyVehicleSnapshot(
        reachable: Boolean,
        propertyListError: String,
    ) = VehicleSnapshot(
        carServiceReachable = reachable,
        propertyServiceAvailable = false,
        propertyListError = propertyListError,
        propertyCount = 0,
        properties = emptyList(),
        readings = emptyList(),
        readPolicy = "Read-only. No vehicle writes or actuator commands are implemented.",
        privacyNote = "VIN, location, and driver identity are not collected.",
    )

    private fun propertyName(id: Int): String = runCatching { VehiclePropertyIds.toString(id) }
        .getOrDefault("PROPERTY_0x${id.toString(16)}")

    private fun formatValue(value: Any?): String? = when (value) {
        null -> null
        is IntArray -> value.joinToString(prefix = "[", postfix = "]")
        is LongArray -> value.joinToString(prefix = "[", postfix = "]")
        is FloatArray -> value.joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> value.joinToString(prefix = "[", postfix = "]")
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { it?.toString() ?: "null" }
        is ByteArray -> "byte[${value.size}]"
        else -> value.toString().take(1_000)
    }

    private fun propertyUnit(id: Int): String? = when (id) {
        VehiclePropertyIds.EV_BATTERY_LEVEL -> "%"
        VehiclePropertyIds.EV_BATTERY_AVERAGE_TEMPERATURE,
        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
        -> "°C"
        VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
        VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
        -> "Wh (VHAL)"
        VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE -> "W (VHAL)"
        VehiclePropertyIds.PERF_VEHICLE_SPEED,
        VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
        -> "m/s (VHAL)"
        VehiclePropertyIds.RANGE_REMAINING -> "m (VHAL)"
        VehiclePropertyIds.PERF_ODOMETER -> "km (VHAL)"
        VehiclePropertyIds.EV_CHARGE_TIME_REMAINING -> "s (VHAL)"
        else -> null
    }

    private fun changeModeName(mode: Int): String = when (mode) {
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS -> "CONTINUOUS"
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE -> "ON_CHANGE"
        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC -> "STATIC"
        else -> "UNKNOWN_$mode"
    }

    private companion object {
        /**
         * Informational and energy properties only. No locks, doors, climate, gear,
         * lights, charging controls, or other actuators are present.
         */
        val allowedReadProperties = listOf(
            VehiclePropertyIds.INFO_MAKE,
            VehiclePropertyIds.INFO_MODEL,
            VehiclePropertyIds.INFO_MODEL_YEAR,
            VehiclePropertyIds.INFO_MODEL_TRIM,
            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
            VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
            VehiclePropertyIds.EV_BATTERY_AVERAGE_TEMPERATURE,
            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
            VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
            VehiclePropertyIds.EV_CHARGE_STATE,
            VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
            VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
            VehiclePropertyIds.RANGE_REMAINING,
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
            VehiclePropertyIds.PERF_ODOMETER,
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
            VehiclePropertyIds.INSTANTANEOUS_EV_EFFICIENCY,
        )
    }
}
