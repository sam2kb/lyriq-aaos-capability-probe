package com.sam2kb.lyriqprobe.core.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val humanFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
    .withZone(ZoneOffset.UTC)

fun utcTimestamp(instant: Instant = Instant.now()): String = instant.toString()

fun humanTimestamp(instant: Instant = Instant.now()): String = humanFormatter.format(instant)

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "unknown"
    if (bytes < 1024) return "$bytes B"
    val kib = bytes / 1024.0
    if (kib < 1024) return String.format(Locale.US, "%.1f KiB", kib)
    val mib = kib / 1024.0
    if (mib < 1024) return String.format(Locale.US, "%.1f MiB", mib)
    return String.format(Locale.US, "%.2f GiB", mib / 1024.0)
}

fun formatFloat(value: Float): String = String.format(Locale.US, "%.4g", value)

fun formatNanosAsMillis(nanos: Long): String =
    String.format(Locale.US, "%.1f ms", nanos / 1_000_000.0)

fun percent(part: Int, whole: Int): String =
    if (whole <= 0) "n/a" else String.format(Locale.US, "%d%%", (part * 100.0 / whole).toInt())
