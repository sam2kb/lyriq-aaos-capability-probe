package com.sam2kb.lyriqprobe.core.report

import com.sam2kb.lyriqprobe.core.model.ProbeReport
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

object ReportJson {
    const val SCHEMA_VERSION = "1.0"

    @OptIn(ExperimentalSerializationApi::class)
    val format: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(report: ProbeReport): String = format.encodeToString(ProbeReport.serializer(), report)

    fun decode(json: String): ProbeReport = format.decodeFromString(ProbeReport.serializer(), json)
}
