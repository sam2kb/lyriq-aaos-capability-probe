package com.sam2kb.lyriqprobe.report

import android.content.Context
import com.sam2kb.lyriqprobe.core.model.ProbeReport
import com.sam2kb.lyriqprobe.core.report.ReportJson
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SavedReport(
    val jsonFile: File,
    val summaryFile: File?,
    val displayName: String,
    val timestamp: String,
    val sizeBytes: Long,
)

class ReportStore(private val context: Context) {
    private val directory: File get() = File(context.filesDir, "reports").apply { mkdirs() }

    fun save(report: ProbeReport): SavedReport {
        val json = ReportJson.encode(report)
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.parse(report.timestamp))
        var suffix = 1
        var base = "lyriq-probe-$stamp"
        while (File(directory, "$base.json").exists()) {
            suffix++
            base = "lyriq-probe-$stamp-$suffix"
        }
        val jsonFile = File(directory, "$base.json")
        val summaryFile = File(directory, "$base.txt")
        jsonFile.writeText(json, Charsets.UTF_8)
        summaryFile.writeText(report.summary.humanReadable, Charsets.UTF_8)
        return SavedReport(
            jsonFile = jsonFile,
            summaryFile = summaryFile,
            displayName = "$base.json",
            timestamp = report.timestamp,
            sizeBytes = jsonFile.length(),
        )
    }

    fun list(): List<SavedReport> = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "json" }
        .sortedByDescending { it.lastModified() }
        .map { jsonFile ->
            val base = jsonFile.nameWithoutExtension
            SavedReport(
                jsonFile = jsonFile,
                summaryFile = File(directory, "$base.txt").takeIf { it.isFile },
                displayName = jsonFile.name,
                timestamp = Instant.ofEpochMilli(jsonFile.lastModified()).toString(),
                sizeBytes = jsonFile.length(),
            )
        }

    fun readJson(saved: SavedReport): String = saved.jsonFile.readText(Charsets.UTF_8)

    fun jsonFor(report: ProbeReport): String = ReportJson.encode(report)

    fun makeShareFile(json: String, displayName: String): File {
        val shareDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(shareDir, displayName)
        file.writeText(json, Charsets.UTF_8)
        return file
    }

    fun remove(saved: SavedReport): Boolean {
        val json = saved.jsonFile.delete()
        saved.summaryFile?.delete()
        return json
    }

    fun latest(): SavedReport? = list().firstOrNull()
}
