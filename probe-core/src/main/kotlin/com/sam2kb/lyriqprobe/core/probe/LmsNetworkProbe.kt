package com.sam2kb.lyriqprobe.core.probe

import android.content.Context
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import com.sam2kb.lyriqprobe.core.model.LmsEndpointResult
import com.sam2kb.lyriqprobe.core.model.LmsNetworkSnapshot
import com.sam2kb.lyriqprobe.core.model.ProbeModule
import com.sam2kb.lyriqprobe.core.model.ProbeSection
import com.sam2kb.lyriqprobe.core.model.ResultStatus
import com.sam2kb.lyriqprobe.core.model.TestOutcome
import com.sam2kb.lyriqprobe.core.model.TestResult
import com.sam2kb.lyriqprobe.core.runner.TestHarness
import com.sam2kb.lyriqprobe.core.runner.activeMeta
import com.sam2kb.lyriqprobe.core.runner.discoveryMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LmsProbeResult(
    val snapshot: LmsNetworkSnapshot?,
    val result: TestResult,
)

/**
 * LMS/LAN readiness for the SqueezeClient port.
 *
 * The endpoint hostname/IP is intentionally not written to the report. It is only used
 * for the user-initiated TCP test; the report keeps the endpoint class, ports, and outcome.
 *
 * SqueezeClient needs HTTP (normally 9000) for the LMS API and Slimproto (3483) for the
 * local player. Android 17/target 37 will require ACCESS_LOCAL_NETWORK for these LAN
 * sockets; Android 16/target 36 currently uses the legacy implicit grant path.
 */
class LmsNetworkProbe(
    private val context: Context,
    private val harness: TestHarness = TestHarness(),
) {
    suspend fun scan(): ProbeSection<LmsNetworkSnapshot> {
        val internet = granted("android.permission.INTERNET")
        val localDefined = permissionDefined("android.permission.ACCESS_LOCAL_NETWORK")
        val localGranted = localDefined && granted("android.permission.ACCESS_LOCAL_NETWORK")
        val nearbyDefined = permissionDefined("android.permission.NEARBY_WIFI_DEVICES")
        val cleartext = runCatching {
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
        }.getOrDefault(false)
        val snapshot = LmsNetworkSnapshot(
            internetPermissionGranted = internet,
            localNetworkPermissionDefined = localDefined,
            localNetworkPermissionGranted = localGranted,
            nearbyWifiPermissionDefined = nearbyDefined,
            cleartextTrafficPermitted = cleartext,
            endpointKind = null,
            note = "SqueezeClient uses HTTP (API) and TCP 3483 (Slimproto). Android 17/target 37 requires " +
                "ACCESS_LOCAL_NETWORK for local-network sockets; this probe targets 36 and tests the current path. " +
                "The user-entered host is not included in the report.",
        )
        val result = harness.run(
            discoveryMeta(
                "lms.network_policy",
                ProbeModule.CONNECTIVITY,
                "LMS network policy and permissions",
                "NetworkSecurityPolicy / PackageManager",
            ),
        ) {
            TestOutcome(
                status = if (internet) ResultStatus.AVAILABLE else ResultStatus.DENIED,
                summary = "INTERNET=$internet; local-network permission defined=$localDefined " +
                    "granted=$localGranted; cleartext=$cleartext.",
                details = buildString {
                    appendLine("ACCESS_LOCAL_NETWORK is a target-37/Android-17 runtime permission.")
                    appendLine("NEARBY_WIFI_DEVICES defined=$nearbyDefined.")
                    appendLine("INTERNET is an install-time permission.")
                },
                measurements = mapOf(
                    "internet" to internet.toString(),
                    "local_network_defined" to localDefined.toString(),
                    "local_network_granted" to localGranted.toString(),
                    "cleartext" to cleartext.toString(),
                ),
            )
        }
        return ProbeSection(snapshot, listOf(result))
    }

    suspend fun testEndpoint(
        host: String,
        httpPort: Int,
        slimprotoPort: Int,
    ): LmsProbeResult = withContext(Dispatchers.IO) {
        var tested: List<LmsEndpointResult> = emptyList()
        var kind: String? = null
        val cleanHost = normalizeHost(host)
        val result = harness.run(
            activeMeta(
                id = "lms.tcp_ports",
                module = ProbeModule.CONNECTIVITY,
                name = "Test LMS HTTP and Slimproto TCP reachability",
                apiUsed = "java.net.Socket",
                timeoutMs = 20_000,
            ),
        ) {
            if (cleanHost.isBlank()) {
                return@run TestOutcome(ResultStatus.NOT_TESTED, "No LMS host was entered.")
            }
            if (httpPort !in 1..65_535 || slimprotoPort !in 1..65_535) {
                return@run TestOutcome(ResultStatus.ERROR, "Both ports must be between 1 and 65535.")
            }
            if (context.checkSelfPermission("android.permission.INTERNET") !=
                PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("android.permission.INTERNET is not granted.")
            }
            kind = endpointKind(cleanHost)
            val resolved = try {
                InetAddress.getAllByName(cleanHost)
            } catch (unknown: UnknownHostException) {
                tested = listOf(
                    failed("http", httpPort, "Host did not resolve."),
                    failed("slimproto", slimprotoPort, "Host did not resolve."),
                )
                return@run TestOutcome(
                    status = ResultStatus.UNAVAILABLE,
                    summary = "The supplied LMS endpoint did not resolve.",
                    exceptionClass = unknown.javaClass.name,
                    exceptionMessage = "Host did not resolve.",
                )
            }
            val address = resolved.first()
            tested = listOf(
                testPort("http", address, httpPort),
                testPort("slimproto", address, slimprotoPort),
            )
            val connected = tested.count { it.connected }
            TestOutcome(
                status = when {
                    connected == tested.size -> ResultStatus.SUCCESS
                    connected > 0 -> ResultStatus.AVAILABLE
                    tested.any { it.errorClass?.contains("SecurityException") == true } -> ResultStatus.DENIED
                    else -> ResultStatus.UNAVAILABLE
                },
                summary = "Connected to $connected/${tested.size} LMS TCP port(s) for a $kind endpoint.",
                details = tested.joinToString("\n") {
                    "${it.label}: port=${it.port}, dns=${it.dnsResolved}, connected=${it.connected}, " +
                        "latency=${it.latencyMs ?: "?"} ms, error=${it.errorClass ?: "none"}"
                },
                measurements = tested.associate { "${it.label}_connected" to it.connected.toString() },
            )
        }
        LmsProbeResult(
            snapshot = LmsNetworkSnapshot(
                internetPermissionGranted = granted("android.permission.INTERNET"),
                localNetworkPermissionDefined = permissionDefined("android.permission.ACCESS_LOCAL_NETWORK"),
                localNetworkPermissionGranted = granted("android.permission.ACCESS_LOCAL_NETWORK"),
                nearbyWifiPermissionDefined = permissionDefined("android.permission.NEARBY_WIFI_DEVICES"),
                cleartextTrafficPermitted = runCatching {
                    NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
                }.getOrDefault(false),
                endpointKind = kind,
                lastTest = tested,
                note = "Endpoint host is omitted from the report. Ports are user-supplied; Slimproto is normally 3483.",
            ),
            result = result,
        )
    }

    private fun testPort(label: String, address: InetAddress, port: Int): LmsEndpointResult {
        val started = System.nanoTime()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), 3_000)
                LmsEndpointResult(
                    label = label,
                    port = port,
                    dnsResolved = true,
                    connected = true,
                    latencyMs = (System.nanoTime() - started) / 1_000_000,
                    errorClass = null,
                    errorMessage = null,
                )
            }
        } catch (timeout: SocketTimeoutException) {
            failed(label, port, "TCP connect timed out.", timeout)
        } catch (refused: ConnectException) {
            failed(label, port, "Connection refused or unreachable.", refused)
        } catch (security: SecurityException) {
            failed(label, port, "Local network access denied.", security)
        } catch (throwable: Throwable) {
            failed(label, port, "TCP connect failed.", throwable)
        }
    }

    private fun failed(
        label: String,
        port: Int,
        message: String,
        throwable: Throwable? = null,
    ) = LmsEndpointResult(
        label = label,
        port = port,
        dnsResolved = throwable !is UnknownHostException,
        connected = false,
        latencyMs = null,
        errorClass = throwable?.javaClass?.name,
        errorMessage = message,
    )

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun permissionDefined(name: String): Boolean = try {
        context.packageManager.getPermissionInfo(name, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun normalizeHost(host: String): String = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .trim('[', ']')

    companion object {
        fun endpointKind(rawHost: String): String {
            val host = rawHost.trim().removePrefix("http://").removePrefix("https://")
                .substringBefore('/').trim('[', ']')
            if (host.equals("localhost", ignoreCase = true)) return "LOOPBACK"
            if (host.endsWith(".local", ignoreCase = true)) return "MDNS"
            if (host.contains(':')) return "IPV6"
            val octets = host.split('.')
            if (octets.size == 4 && octets.all { part ->
                    part.toIntOrNull()?.let { it in 0..255 } == true
                }) {
                val first = octets[0].toInt()
                val second = octets[1].toInt()
                return when {
                    first == 10 || (first == 192 && second == 168) ||
                        (first == 172 && second in 16..31) -> "PRIVATE_IPV4"
                    first == 127 -> "LOOPBACK"
                    else -> "PUBLIC_IPV4"
                }
            }
            return "HOSTNAME"
        }
    }
}
