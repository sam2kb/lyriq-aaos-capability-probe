package com.sam2kb.lyriqprobe.core.probe

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.sam2kb.lyriqprobe.core.model.AndroidSensorInfo
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.SensorSample
import com.sam2kb.lyriqprobe.core.model.SensorSnapshot
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import com.sam2kb.lyriqprobe.core.util.formatFloat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

data class SensorSampleResult(
    val sample: SensorSample?,
    val result: TestResult,
)

class SensorProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private var lastSample: SensorSample? = null

    suspend fun scan(): ProbeSection<SensorSnapshot> {
        val manager = sensorManager
        if (manager == null) {
            return ProbeSection(
                snapshot = SensorSnapshot(emptyList(), "SensorManager is unavailable."),
                results = listOf(
                    harness.run(
                        discoveryMeta(
                            "sensors.inventory",
                            ProbeModule.SENSORS,
                            "Android sensor inventory",
                            "SensorManager.getSensorList",
                        ),
                    ) {
                        TestOutcome(ResultStatus.UNSUPPORTED, "SensorManager is unavailable.")
                    },
                ),
            )
        }
        val sensors = manager.getSensorList(Sensor.TYPE_ALL).mapNotNull(::toInfo)
            .sortedWith(compareBy({ it.type }, { it.name }))
        val snapshot = SensorSnapshot(
            sensors = sensors,
            note = "Android SensorManager sensors exposed to this app. These are not the vehicle's VHAL " +
                "properties and are not the same list a phone running Android Auto would expose.",
            lastSample = lastSample,
        )
        val result = harness.run(
            discoveryMeta(
                "sensors.inventory",
                ProbeModule.SENSORS,
                "Android sensor inventory",
                "SensorManager.getSensorList(Sensor.TYPE_ALL)",
            ),
        ) {
            TestOutcome(
                status = if (sensors.isEmpty()) ResultStatus.NOT_EXPOSED else ResultStatus.AVAILABLE,
                summary = if (sensors.isEmpty()) {
                    "No Android sensors are exposed to this app."
                } else {
                    "${sensors.size} Android sensor(s) exposed; ${sensors.count { it.wakeUpSensor }} wake-up sensor(s)."
                },
                details = sensors.joinToString("\n") {
                    "${it.name} [${it.stringType}/${it.type}] vendor=${it.vendor} " +
                        "range=${it.maximumRange} resolution=${it.resolution} reporting=${it.reportingMode}"
                },
                measurements = mapOf("sensor_count" to sensors.size.toString()),
            )
        }
        return ProbeSection(snapshot, listOf(result))
    }

    suspend fun sample(
        sensorType: Int,
        canContinue: () -> Boolean = { true },
    ): SensorSampleResult = withContext(Dispatchers.IO) {
        var sample: SensorSample? = null
        val result = harness.run(
            activeMeta(
                id = "sensors.sample.$sensorType",
                module = ProbeModule.SENSORS,
                name = "Sample Android sensor $sensorType for 10 seconds",
                apiUsed = "SensorManager.registerListener",
                timeoutMs = 14_000,
            ),
        ) {
            val manager = sensorManager
                ?: return@run TestOutcome(ResultStatus.UNSUPPORTED, "SensorManager is unavailable.")
            val sensor = manager.getSensorList(sensorType).firstOrNull()
                ?: return@run TestOutcome(
                    ResultStatus.NOT_EXPOSED,
                    "No sensor of type $sensorType is exposed to this app.",
                )
            val stats = SampleStats(sensor)
            val thread = HandlerThread("lyriq-sensor-${sensor.type}").apply { start() }
            val handler = Handler(thread.looper)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    stats.add(event)
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    stats.accuracy = accuracy
                }
            }
            try {
                val registered = manager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                    handler,
                )
                if (!registered) {
                    return@run TestOutcome(
                        ResultStatus.UNSUPPORTED,
                        "SensorManager.registerListener returned false for ${sensor.name}.",
                    )
                }
                val start = SystemClock.elapsedRealtime()
                var interrupted = false
                while (SystemClock.elapsedRealtime() - start < SAMPLE_DURATION_MS) {
                    delay(100)
                    if (!canContinue()) {
                        interrupted = true
                        break
                    }
                }
                val elapsed = SystemClock.elapsedRealtime() - start
                sample = stats.toSample(elapsed)
                val partial = sample!!
                when {
                    interrupted -> TestOutcome(
                        status = ResultStatus.NOT_TESTED,
                        summary = "Sampling stopped early because the parked/driving guard changed.",
                        details = "Only ${partial.eventCount} event(s) were captured before the guard stopped the test.",
                        measurements = sampleMeasurements(partial),
                    )
                    partial.eventCount == 0 -> TestOutcome(
                        status = ResultStatus.TIMEOUT,
                        summary = "No events from ${sensor.name} in ${elapsed} ms. On-change sensors may only report when conditions change.",
                        measurements = sampleMeasurements(partial),
                    )
                    else -> TestOutcome(
                        status = ResultStatus.SUCCESS,
                        summary = "${partial.eventCount} events from ${sensor.name} in ${elapsed} ms.",
                        details = sampleDetails(sensor, partial, stats.accuracy),
                        measurements = sampleMeasurements(partial),
                    )
                }
            } finally {
                runCatching { manager.unregisterListener(listener) }
                thread.quitSafely()
            }
        }
        lastSample = sample
        SensorSampleResult(sample, result)
    }

    private fun sampleMeasurements(sample: SensorSample): Map<String, String> = mapOf(
        "event_count" to sample.eventCount.toString(),
        "duration_ms" to sample.durationMs.toString(),
        "axis_min" to sample.minValues.joinToString(",") { formatFloat(it) },
        "axis_max" to sample.maxValues.joinToString(",") { formatFloat(it) },
        "axis_mean" to sample.meanValues.joinToString(",") { formatFloat(it) },
    )

    private fun sampleDetails(sensor: Sensor, sample: SensorSample, accuracy: Int): String = buildString {
        appendLine("Sensor: ${sensor.name}")
        appendLine("Type: ${sensor.stringType} (${sensor.type})")
        appendLine("Vendor: ${sensor.vendor}")
        appendLine("Reporting mode: ${reportingModeName(sensor.reportingMode)}")
        appendLine("Accuracy callback: $accuracy")
        appendLine("Events: ${sample.eventCount}")
        appendLine("Axis min: ${sample.minValues}")
        appendLine("Axis max: ${sample.maxValues}")
        appendLine("Axis mean: ${sample.meanValues}")
        appendLine("Last values: ${sample.lastValues}")
        appendLine("Listener unregistered in finally; no background sampling remains.")
    }

    private fun toInfo(sensor: Sensor): AndroidSensorInfo? = runCatching {
        AndroidSensorInfo(
            type = sensor.type,
            stringType = sensor.stringType,
            name = sensor.name,
            vendor = sensor.vendor,
            version = sensor.version,
            id = sensor.id,
            resolution = sensor.resolution,
            maximumRange = sensor.maximumRange,
            powerMilliAmp = sensor.power,
            minDelayMicros = sensor.minDelay,
            maxDelayMicros = sensor.maxDelay,
            reportingMode = reportingModeName(sensor.reportingMode),
            wakeUpSensor = sensor.isWakeUpSensor,
            dynamicSensor = sensor.isDynamicSensor,
            additionalInfoSupported = sensor.isAdditionalInfoSupported,
            fifoMaxEventCount = sensor.fifoMaxEventCount,
            highestDirectReportRateLevel = sensor.highestDirectReportRateLevel,
        )
    }.getOrNull()

    private fun reportingModeName(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS -> "CONTINUOUS"
        Sensor.REPORTING_MODE_ON_CHANGE -> "ON_CHANGE"
        Sensor.REPORTING_MODE_ONE_SHOT -> "ONE_SHOT"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "SPECIAL_TRIGGER"
        else -> "UNKNOWN_$mode"
    }

    private class SampleStats(private val sensor: Sensor) {
        private val lock = Any()
        private var min: FloatArray? = null
        private var max: FloatArray? = null
        private var sum: DoubleArray? = null
        private var last: FloatArray = FloatArray(0)
        private var timestamp: Long? = null
        private var count = 0
        var accuracy: Int = -1

        fun add(event: SensorEvent) {
            synchronized(lock) {
                val values = event.values.copyOf()
                if (min == null || values.size != min!!.size) {
                    min = values.copyOf()
                    max = values.copyOf()
                    sum = DoubleArray(values.size)
                }
                values.forEachIndexed { index, value ->
                    if (value < min!![index]) min!![index] = value
                    if (value > max!![index]) max!![index] = value
                    sum!![index] += value.toDouble()
                }
                last = values
                timestamp = event.timestamp
                count++
            }
        }

        fun toSample(durationMs: Long): SensorSample = synchronized(lock) {
            val minValues = min?.toList().orEmpty()
            val maxValues = max?.toList().orEmpty()
            val means = sum?.map { (it / count.coerceAtLeast(1)).toFloat() }.orEmpty()
            SensorSample(
                sensorType = sensor.type,
                sensorName = sensor.name,
                durationMs = durationMs,
                eventCount = count,
                minValues = minValues,
                maxValues = maxValues,
                meanValues = means,
                lastValues = last.toList(),
                timestampNanos = timestamp,
                summary = "$count event(s); " + (
                    if (minValues.isEmpty()) {
                        "no axes observed."
                    } else {
                        "RMS magnitude ${formatFloat(rms(last))} across ${last.size} axis/axes."
                    }
                    ),
            )
        }

        private fun rms(values: FloatArray): Float {
            if (values.isEmpty()) return 0f
            return sqrt(values.sumOf { (it * it).toDouble() }.toFloat() / values.size)
        }
    }

    private companion object {
        const val SAMPLE_DURATION_MS = 10_000L
    }
}
