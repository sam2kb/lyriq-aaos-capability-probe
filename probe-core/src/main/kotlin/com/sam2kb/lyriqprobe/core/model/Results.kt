package com.sam2kb.lyriqprobe.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ResultStatus {
    SUCCESS,
    AVAILABLE,
    DENIED,
    PRIVILEGED_ONLY,
    UNSUPPORTED,
    NOT_EXPOSED,
    UNAVAILABLE,
    ERROR,
    TIMEOUT,
    NOT_TESTED,
}

@Serializable
enum class TestKind {
    DISCOVERY,
    ACTIVE,
}

@Serializable
enum class ProbeModule {
    SYSTEM,
    PERMISSIONS,
    CAMERAS,
    VEHICLE,
    SENSORS,
    STORAGE,
    CONNECTIVITY,
    VPN,
    DRIVING,
    REPORTING,
}

@Serializable
data class TestResult(
    val id: String,
    val module: ProbeModule,
    val kind: TestKind,
    val name: String,
    val status: ResultStatus,
    val summary: String,
    val details: String? = null,
    @SerialName("api_used")
    val apiUsed: String,
    @SerialName("elapsed_ms")
    val elapsedMs: Long,
    val timestamp: String,
    @SerialName("exception_class")
    val exceptionClass: String? = null,
    @SerialName("exception_message")
    val exceptionMessage: String? = null,
    val measurements: Map<String, String> = emptyMap(),
)

@Serializable
data class ProbeError(
    val module: ProbeModule,
    @SerialName("test_id")
    val testId: String,
    val message: String,
    @SerialName("exception_class")
    val exceptionClass: String? = null,
    val timestamp: String,
)

data class TestMeta(
    val id: String,
    val module: ProbeModule,
    val kind: TestKind,
    val name: String,
    val apiUsed: String,
    val timeoutMs: Long? = null,
)

data class TestOutcome(
    val status: ResultStatus,
    val summary: String,
    val details: String? = null,
    val measurements: Map<String, String> = emptyMap(),
    val exceptionClass: String? = null,
    val exceptionMessage: String? = null,
)

/**
 * A single probe section. A failure in one section must never prevent the others
 * from being collected, so each section carries its own results and optional fatal
 * error string.
 */
data class ProbeSection<T>(
    val snapshot: T? = null,
    val results: List<TestResult> = emptyList(),
    val fatalError: String? = null,
)

fun TestResult.isFailure(): Boolean = status in setOf(
    ResultStatus.DENIED,
    ResultStatus.PRIVILEGED_ONLY,
    ResultStatus.UNSUPPORTED,
    ResultStatus.NOT_EXPOSED,
    ResultStatus.UNAVAILABLE,
    ResultStatus.ERROR,
    ResultStatus.TIMEOUT,
)
