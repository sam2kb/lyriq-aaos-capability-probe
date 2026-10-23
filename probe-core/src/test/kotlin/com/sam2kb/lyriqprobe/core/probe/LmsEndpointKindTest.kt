package com.sam2kb.lyriqprobe.core.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class LmsEndpointKindTest {
    @Test
    fun classifiesCommonEndpointsWithoutNetworkAccess() {
        assertEquals("PRIVATE_IPV4", LmsNetworkProbe.endpointKind("192.168.1.20"))
        assertEquals("PRIVATE_IPV4", LmsNetworkProbe.endpointKind("10.0.0.5"))
        assertEquals("PUBLIC_IPV4", LmsNetworkProbe.endpointKind("8.8.8.8"))
        assertEquals("LOOPBACK", LmsNetworkProbe.endpointKind("localhost"))
        assertEquals("MDNS", LmsNetworkProbe.endpointKind("lms.local"))
        assertEquals("HOSTNAME", LmsNetworkProbe.endpointKind("music.example"))
        assertEquals("IPV6", LmsNetworkProbe.endpointKind("[fd00::1]"))
    }
}
