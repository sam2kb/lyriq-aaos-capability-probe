package com.sam2kb.lyriqprobe.core.runner

import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestKind
import com.sam2kb.lyriqprobe.core.model.TestMeta
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.time.Instant

/**
 * Runs one test with a hard timeout, converts throwables into structured results, and
 * measures elapsed time. It never swallows cancellation of the wider scan.
 */
class TestHarness(
    private val clock: Clock = Clock.systemUTC(),
    private val defaultTimeoutMs: Long = 5_000,
) {
    suspend fun run(meta: TestMeta, block: suspend () -> TestOutcome): TestResult {
        val started = clock.millis()
        val startedTimestamp = timestamp(started)
        return try {
            val outcome = withTimeout(meta.timeoutMs ?: defaultTimeoutMs) { block() }
            buildResult(meta, outcome, started, startedTimestamp)
        } catch (timeout: TimeoutCancellationException) {
            buildResult(
                meta = meta,
                outcome = TestOutcome(
                    status = ResultStatus.TIMEOUT,
                    summary = "Timed out after ${meta.timeoutMs ?: defaultTimeoutMs} ms.",
                    exceptionClass = timeout.javaClass.name,
                    exceptionMessage = timeout.message,
                ),
                started = started,
                startedTimestamp = startedTimestamp,
            )
        } catch (cancelled: CancellationException) {
            // Parent scope cancellation (screen leaving, driving restriction) is not a test result.
            throw cancelled
        } catch (throwable: Throwable) {
            buildResult(meta, throwable.toOutcome(), started, startedTimestamp)
        }
    }

    private fun buildResult(
        meta: TestMeta,
        outcome: TestOutcome,
        started: Long,
        startedTimestamp: String,
    ) = TestResult(
        id = meta.id,
        module = meta.module,
        kind = meta.kind,
        name = meta.name,
        status = outcome.status,
        summary = outcome.summary,
        details = outcome.details,
        apiUsed = meta.apiUsed,
        elapsedMs = (clock.millis() - started).coerceAtLeast(0),
        timestamp = startedTimestamp,
        exceptionClass = outcome.exceptionClass,
        exceptionMessage = outcome.exceptionMessage,
        measurements = outcome.measurements,
    )

    private fun Throwable.toOutcome(): TestOutcome {
        val status = when (this) {
            is SecurityException -> ResultStatus.DENIED
            is UnsupportedOperationException -> ResultStatus.UNSUPPORTED
            is NoClassDefFoundError, is ClassNotFoundException -> ResultStatus.UNSUPPORTED
            else -> ResultStatus.ERROR
        }
        val explanation = when (status) {
            ResultStatus.DENIED -> "The platform or permission model denied this test."
            ResultStatus.UNSUPPORTED -> "This API or class is not available on this build."
            else -> "The test threw an unexpected exception."
        }
        return TestOutcome(
            status = status,
            summary = "$explanation ${javaClass.simpleName}: ${message ?: "(no message)"}",
            details = stackTraceToString(),
            exceptionClass = javaClass.name,
            exceptionMessage = message,
        )
    }

    private fun timestamp(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}

fun discoveryMeta(
    id: String,
    module: ProbeModule,
    name: String,
    apiUsed: String,
    timeoutMs: Long? = null,
) = TestMeta(id, module, TestKind.DISCOVERY, name, apiUsed, timeoutMs)

fun activeMeta(
    id: String,
    module: ProbeModule,
    name: String,
    apiUsed: String,
    timeoutMs: Long? = null,
) = TestMeta(id, module, TestKind.ACTIVE, name, apiUsed, timeoutMs)
