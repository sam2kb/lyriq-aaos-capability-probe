package com.sam2kb.lyriqprobe.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun bytesUseStableBinaryUnits() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KiB", formatBytes(1024))
        assertEquals("1.0 MiB", formatBytes(1024 * 1024))
        assertEquals("1.00 GiB", formatBytes(1024L * 1024L * 1024L))
        assertEquals("unknown", formatBytes(null))
    }

    @Test
    fun percentAvoidsDivisionByZero() {
        assertEquals("n/a", percent(1, 0))
        assertEquals("50%", percent(1, 2))
    }
}
