package com.sam2kb.lyriqprobe.core.permission

import android.content.pm.PermissionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionClassifierTest {
    @Test
    fun dangerousPermissionIsRuntimeRequestableInPrinciple() {
        val result = PermissionClassifier.classify(PermissionInfo.PROTECTION_DANGEROUS)

        assertEquals("DANGEROUS", result.base)
        assertTrue(result.dangerous)
        assertFalse(result.signatureOnly)
    }

    @Test
    fun signaturePrivilegedPermissionIsProtected() {
        val raw = PermissionInfo.PROTECTION_SIGNATURE or PermissionInfo.PROTECTION_FLAG_PRIVILEGED
        val result = PermissionClassifier.classify(raw)

        assertEquals("SIGNATURE", result.base)
        assertTrue(result.signatureOnly)
        assertTrue(result.privileged)
        assertFalse(result.dangerous)
        assertTrue("PRIVILEGED" in result.flags)
    }

    @Test
    fun normalPermissionIsNotRuntimeRequestable() {
        val result = PermissionClassifier.classify(PermissionInfo.PROTECTION_NORMAL)

        assertEquals("NORMAL", result.base)
        assertFalse(result.dangerous)
    }
}
