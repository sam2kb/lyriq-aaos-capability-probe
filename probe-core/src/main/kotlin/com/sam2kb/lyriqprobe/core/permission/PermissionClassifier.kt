package com.sam2kb.lyriqprobe.core.permission

import android.content.pm.PermissionInfo

data class ProtectionSummary(
    val base: String,
    val flags: List<String>,
    val dangerous: Boolean,
    val signatureOnly: Boolean,
    val privileged: Boolean,
)

object PermissionClassifier {
    fun classify(rawProtection: Int): ProtectionSummary {
        val base = when (rawProtection and PermissionInfo.PROTECTION_MASK_BASE) {
            PermissionInfo.PROTECTION_NORMAL -> "NORMAL"
            PermissionInfo.PROTECTION_DANGEROUS -> "DANGEROUS"
            PermissionInfo.PROTECTION_SIGNATURE -> "SIGNATURE"
            else -> "UNKNOWN"
        }
        val flags = buildList {
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0) add("PRIVILEGED")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_SYSTEM != 0) add("SYSTEM")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT != 0) add("DEVELOPMENT")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_APPOP != 0) add("APPOP")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_INSTALLER != 0) add("INSTALLER")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_VERIFIER != 0) add("VERIFIER")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_PREINSTALLED != 0) add("PREINSTALLED")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_SETUP != 0) add("SETUP")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_INSTANT != 0) add("INSTANT")
            if (rawProtection and PermissionInfo.PROTECTION_FLAG_RUNTIME_ONLY != 0) add("RUNTIME_ONLY")
        }
        return ProtectionSummary(
            base = base,
            flags = flags,
            dangerous = base == "DANGEROUS",
            signatureOnly = base == "SIGNATURE",
            privileged = rawProtection and PermissionInfo.PROTECTION_FLAG_PRIVILEGED != 0,
        )
    }
}
