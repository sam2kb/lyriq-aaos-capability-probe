package com.sam2kb.lyriqprobe.core.runner

import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestKind
import com.sam2kb.lyriqprobe.core.model.TestMeta
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TestHarnessTest {
    private fun meta(timeoutMs: Long? = null) = TestMeta(
        id = "test.id",
        module = ProbeModule.SYSTEM,
        kind = TestKind.ACTIVE,
        name = "Test",
        apiUsed = "test",
        timeoutMs = timeoutMs,
    )

    @Test
    fun timeoutIsReportedAsTimeout() = runTest {
        val result = TestHarness(defaultTimeoutMs = 20).run(meta()) {
            delay(1_000)
            TestOutcome(ResultStatus.SUCCESS, "never reached")
        }

        assertEquals(ResultStatus.TIMEOUT, result.status)
        assertEquals("test.id", result.id)
        assertEquals("TimeoutCancellationException", result.exceptionClass?.substringAfterLast('.'))
    }

    @Test
    fun securityExceptionIsDenied() = runTest {
        val result = TestHarness().run(meta()) {
            throw SecurityException("permission denied")
        }

        assertEquals(ResultStatus.DENIED, result.status)
        assertEquals("java.lang.SecurityException", result.exceptionClass)
        assertEquals("permission denied", result.exceptionMessage)
    }

    @Test
    fun unsupportedOperationIsUnsupported() = runTest {
        val result = TestHarness().run(meta()) {
            throw UnsupportedOperationException("not available")
        }

        assertEquals(ResultStatus.UNSUPPORTED, result.status)
    }

    @Test
    fun successfulOutcomeIsPreserved() = runTest {
        val result = TestHarness().run(meta()) {
            TestOutcome(
                status = ResultStatus.AVAILABLE,
                summary = "inventory complete",
                measurements = mapOf("count" to "3"),
            )
        }

        assertEquals(ResultStatus.AVAILABLE, result.status)
        assertEquals("inventory complete", result.summary)
        assertEquals("3", result.measurements["count"])
        assertNull(result.exceptionClass)
    }
}
