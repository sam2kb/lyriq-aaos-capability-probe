package com.sam2kb.lyriqprobe.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A deliberately minimal VpnService used to prove whether AAOS will authorize and
 * create a TUN interface for an ordinary app.
 *
 * It adds no routes, DNS servers, or application rules. The interface is closed in the
 * same worker thread and the service stops immediately.
 */
class VpnProbeService : VpnService() {
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_ESTABLISH) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!running.compareAndSet(false, true)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Thread({
            val started = SystemClock.elapsedRealtime()
            var tun: ParcelFileDescriptor? = null
            var failure: Throwable? = null
            var closed: Boolean? = null
            try {
                val builder = Builder()
                    .setSession(SESSION_NAME)
                    .setMtu(1500)
                    .addAddress(PROBE_ADDRESS, 32)
                    .setBlocking(false)
                // Intentionally no addRoute(), addDnsServer(), addAllowedApplication(),
                // addDisallowedApplication(), or setUnderlyingNetworks().
                tun = builder.establish()
                if (tun == null) {
                    failure = IllegalStateException("VpnService.Builder.establish() returned null.")
                }
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                tun?.let { descriptor ->
                    closed = runCatching { descriptor.close() }.isSuccess
                }
            }
            VpnProbeBroker.publish(
                VpnEstablishOutcome(
                    established = tun != null,
                    closed = closed,
                    durationMs = SystemClock.elapsedRealtime() - started,
                    addressesAdded = listOf("$PROBE_ADDRESS/32"),
                    routesAdded = emptyList(),
                    dnsServers = emptyList(),
                    allowedApplications = emptyList(),
                    exceptionClass = failure?.javaClass?.name,
                    exceptionMessage = failure?.message,
                ),
            )
            running.set(false)
            stopSelf(startId)
        }, "lyriq-vpn-probe").start()

        return START_NOT_STICKY
    }

    override fun onRevoke() {
        running.set(false)
        stopSelf()
        super.onRevoke()
    }

    companion object {
        const val ACTION_ESTABLISH = "com.sam2kb.lyriqprobe.vpn.ESTABLISH"
        const val SESSION_NAME = "LYRIQ AAOS capability probe"
        const val PROBE_ADDRESS = "10.254.253.1"
    }
}
