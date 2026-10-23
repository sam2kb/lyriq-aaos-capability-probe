package com.sam2kb.lyriqprobe.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.model.VpnCapabilitySnapshot
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class VpnEstablishOutcome(
    val established: Boolean,
    val closed: Boolean?,
    val durationMs: Long,
    val addressesAdded: List<String>,
    val routesAdded: List<String>,
    val dnsServers: List<String>,
    val allowedApplications: List<String>,
    val exceptionClass: String?,
    val exceptionMessage: String?,
)

object VpnProbeBroker {
    private val mutableEvents = MutableSharedFlow<VpnEstablishOutcome>(extraBufferCapacity = 4)
    val events = mutableEvents.asSharedFlow()

    fun publish(outcome: VpnEstablishOutcome) {
        mutableEvents.tryEmit(outcome)
    }
}

/**
 * Capability test only. It obtains VpnService consent, establishes a TUN interface with
 * one private address and no routes/DNS/app rules, then closes it immediately.
 */
class VpnProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    suspend fun scan(): ProbeSection<VpnCapabilitySnapshot> {
        val apiAvailable = runCatching {
            Class.forName("android.net.VpnService", false, context.classLoader)
            true
        }.getOrDefault(false)
        val component = android.content.ComponentName(context, VpnProbeService::class.java)
        val serviceInfo = runCatching {
            context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)
        }.getOrNull()
        val filter = runCatching {
            context.packageManager.queryIntentServices(
                Intent("android.net.VpnService"),
                PackageManager.GET_META_DATA,
            ).any { it.serviceInfo.name == VpnProbeService::class.java.name }
        }.getOrDefault(false)
        var prepareError: String? = null
        val prepareIntent = try {
            if (apiAvailable) VpnService.prepare(context) else null
        } catch (throwable: Throwable) {
            prepareError = "${throwable.javaClass.simpleName}: ${throwable.message}"
            null
        }
        val authorizationState = when {
            !apiAvailable -> "API_UNAVAILABLE"
            prepareError != null -> "PREPARE_ERROR"
            prepareIntent == null -> "ALREADY_AUTHORIZED"
            else -> "CONSENT_REQUIRED"
        }
        val snapshot = emptySnapshot(
            apiAvailable = apiAvailable,
            serviceDeclared = serviceInfo != null,
            bindPermissionProtected = serviceInfo?.permission ==
                android.Manifest.permission.BIND_VPN_SERVICE,
            serviceExported = serviceInfo?.exported,
            vpnIntentFilterPresent = filter,
            authorizationState = authorizationState,
            error = prepareError,
        )
        val result = harness.run(
            discoveryMeta(
                "vpn.service",
                ProbeModule.VPN,
                "VpnService availability and component declaration",
                "VpnService.prepare + PackageManager",
            ),
        ) {
            TestOutcome(
                status = when {
                    !apiAvailable -> ResultStatus.UNSUPPORTED
                    serviceInfo == null -> ResultStatus.UNSUPPORTED
                    !snapshot.bindPermissionProtected -> ResultStatus.ERROR
                    else -> ResultStatus.AVAILABLE
                },
                summary = when {
                    !apiAvailable -> "VpnService is not available on this platform."
                    serviceInfo == null -> "No VpnService component is declared in this build."
                    authorizationState == "ALREADY_AUTHORIZED" ->
                        "VpnService is declared and the app already holds VPN consent."
                    authorizationState == "CONSENT_REQUIRED" ->
                        "VpnService is declared. The OS reports that VPN consent is required."
                    else -> "VpnService is declared, but prepare() failed: $prepareError"
                },
                details = "Service info: permission=${serviceInfo?.permission}, " +
                    "exported=${serviceInfo?.exported}, vpnIntentFilter=$filter, " +
                    "prepareState=$authorizationState. prepare() was called but no consent dialog was displayed by this scan.",
                measurements = mapOf(
                    "authorization_state" to authorizationState,
                    "service_declared" to (serviceInfo != null).toString(),
                ),
            )
        }
        return ProbeSection(snapshot, listOf(result))
    }

    fun authorizationIntent(): Intent? = VpnService.prepare(context)

    fun emptySnapshot(
        apiAvailable: Boolean = true,
        serviceDeclared: Boolean = true,
        bindPermissionProtected: Boolean = true,
        serviceExported: Boolean? = false,
        vpnIntentFilterPresent: Boolean = true,
        authorizationState: String = "NOT_REQUESTED",
        error: String? = null,
    ) = VpnCapabilitySnapshot(
        apiAvailable = apiAvailable,
        serviceDeclared = serviceDeclared,
        bindPermissionProtected = bindPermissionProtected,
        serviceExported = serviceExported,
        vpnIntentFilterPresent = vpnIntentFilterPresent,
        authorizationState = authorizationState,
        consentDialogRequested = false,
        interfaceEstablished = null,
        interfaceClosed = null,
        sessionName = null,
        addressesAdded = emptyList(),
        routesAdded = emptyList(),
        dnsServers = emptyList(),
        allowedApplications = emptyList(),
        durationMs = null,
        error = error,
        note = "The VPN test establishes a temporary TUN with no routes, DNS servers, or allowed apps. " +
            "It does not carry traffic and is closed immediately. No WireGuard or other VPN client is implemented.",
    )

    suspend fun deniedResult(stage: String): TestResult = harness.run(
        activeMeta(
            id = "vpn.authorization",
            module = ProbeModule.VPN,
            name = "VPN authorization and temporary TUN",
            apiUsed = "VpnService.prepare",
            timeoutMs = 10_000,
        ),
    ) {
        TestOutcome(
            status = ResultStatus.DENIED,
            summary = "VPN authorization was not granted at $stage.",
            details = "No TUN interface was created. The app does not attempt to bypass the system VPN consent dialog.",
        )
    }

    suspend fun failureResult(stage: String, throwable: Throwable): TestResult = harness.run(
        activeMeta(
            id = "vpn.authorization",
            module = ProbeModule.VPN,
            name = "VPN authorization and temporary TUN",
            apiUsed = "VpnService.prepare",
            timeoutMs = 10_000,
        ),
    ) {
        throw IllegalStateException("$stage: ${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
    }

    fun snapshotForConsent(
        base: VpnCapabilitySnapshot?,
        granted: Boolean,
        dialogRequested: Boolean,
    ): VpnCapabilitySnapshot = (base ?: emptySnapshot()).copy(
        authorizationState = if (granted) "AUTHORIZED" else "DENIED",
        consentDialogRequested = dialogRequested,
        error = null,
    )

    fun snapshotForPrepare(
        base: VpnCapabilitySnapshot?,
        state: String,
        dialogRequested: Boolean,
    ): VpnCapabilitySnapshot = (base ?: emptySnapshot()).copy(
        authorizationState = state,
        consentDialogRequested = dialogRequested,
        error = null,
    )

    fun snapshotForOutcome(
        base: VpnCapabilitySnapshot?,
        outcome: VpnEstablishOutcome,
        authorizationState: String,
    ): VpnCapabilitySnapshot = (base ?: emptySnapshot()).copy(
        authorizationState = authorizationState,
        interfaceEstablished = outcome.established,
        interfaceClosed = outcome.closed,
        sessionName = VpnProbeService.SESSION_NAME,
        addressesAdded = outcome.addressesAdded,
        routesAdded = outcome.routesAdded,
        dnsServers = outcome.dnsServers,
        allowedApplications = outcome.allowedApplications,
        durationMs = outcome.durationMs,
        error = outcome.exceptionMessage,
    )
}
