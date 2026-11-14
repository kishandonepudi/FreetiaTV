package com.freetia.tv.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ProbeRulesTest {
    @Test fun statusCodes() {
        assertEquals(Probe.ALIVE, ProbeRules.ofStatus(200))
        assertEquals(Probe.ALIVE, ProbeRules.ofStatus(302))
        assertEquals(Probe.DEAD, ProbeRules.ofStatus(404))
        assertEquals(Probe.DEAD, ProbeRules.ofStatus(503))
    }

    @Test fun connectivityErrorsAreInconclusive() {
        assertEquals(Probe.NETWORK, ProbeRules.ofError(UnknownHostException("x")))
        assertEquals(Probe.NETWORK, ProbeRules.ofError(ConnectException("x")))
        assertEquals(Probe.NETWORK, ProbeRules.ofError(SocketTimeoutException("x")))
        assertEquals(Probe.NETWORK, ProbeRules.ofError(InterruptedIOException("timeout")))
    }

    @Test fun failuresFromAReachableHostAreDead() {
        assertEquals(Probe.DEAD, ProbeRules.ofError(SSLHandshakeException("x")))
        assertEquals(Probe.DEAD, ProbeRules.ofError(IOException("unexpected end of stream")))
    }

    @Test fun headThenGet() {
        assertEquals(Probe.ALIVE, ProbeRules.combine(Probe.DEAD, Probe.ALIVE))
        assertEquals(Probe.DEAD, ProbeRules.combine(Probe.DEAD, Probe.NETWORK))
        assertEquals(Probe.DEAD, ProbeRules.combine(Probe.NETWORK, Probe.DEAD))
        assertEquals(Probe.NETWORK, ProbeRules.combine(Probe.NETWORK, Probe.NETWORK))
    }

    @Test fun offlineOnlyWhenFirstBatchFailsWithConnectivityErrors() {
        assertTrue(ProbeRules.looksOffline(listOf(Probe.NETWORK, Probe.NETWORK), firstBatch = true))
        assertFalse(ProbeRules.looksOffline(listOf(Probe.NETWORK, Probe.DEAD), firstBatch = true))
        assertFalse(ProbeRules.looksOffline(listOf(Probe.NETWORK, Probe.NETWORK), firstBatch = false))
        assertFalse(ProbeRules.looksOffline(emptyList(), firstBatch = true))
    }
}
