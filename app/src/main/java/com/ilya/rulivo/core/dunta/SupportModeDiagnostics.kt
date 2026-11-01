package com.ilya.rulivo.core.dunta

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import com.progun.dunta_sdk.android.core.DuntaService
import com.progun.dunta_sdk.api.DuntaManager
import com.progun.dunta_sdk.proxy.core.BotProtocolProvider
import com.progun.dunta_sdk.proxy.core.channel.ServerChannel
import com.ilya.rulivo.BuildConfig
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object SupportModeDiagnostics {
    const val TAG = "SupportModeDiag"

    private val installed = AtomicBoolean(false)
    private val socketSequence = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val processTxBaseline = uidTxBytes()
    private val processRxBaseline = uidRxBytes()

    fun install(manager: DuntaManager, context: Context) {
        if (!BuildConfig.DEBUG || !installed.compareAndSet(false, true)) return

        manager.setSocketCallback { socket -> onSocketCreated(socket) }
        observeNetwork(context.applicationContext)
        scheduleRuntimeProbe(2_000L)
        scheduleRuntimeProbe(10_000L)
        scheduleRuntimeProbe(30_000L)
        event("diagnostics_installed")
    }

    fun state(
        event: String,
        configured: Boolean,
        peer: Boolean,
        batteryExempt: Boolean? = null,
        result: String? = null
    ) {
        if (!BuildConfig.DEBUG) return
        val fields = buildList {
            add("configured=$configured")
            add("peer=$peer")
            batteryExempt?.let { add("battery_exempt=$it") }
            result?.let { add("result=$it") }
        }
        event(event, *fields.toTypedArray())
    }

    fun networkSnapshot(event: String, connectivityManager: ConnectivityManager?) {
        if (!BuildConfig.DEBUG) return
        val capabilities = connectivityManager
            ?.activeNetwork
            ?.let(connectivityManager::getNetworkCapabilities)
        val transport = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        event(
            event,
            "network=$transport",
            "validated=${capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true}",
            "tx_delta_bytes=${delta(uidTxBytes(), processTxBaseline)}",
            "rx_delta_bytes=${delta(uidRxBytes(), processRxBaseline)}"
        )
    }

    fun runtimeSnapshot(event: String) {
        if (!BuildConfig.DEBUG) return
        val proxyClient = runCatching { DuntaService.proxyClient }.getOrNull()
        event(
            event,
            "service_running=${runCatching { DuntaService.serviceIsRunning }.getOrDefault(false)}",
            "client_running=${runCatching { DuntaService.clientIsRunning }.getOrDefault(false)}",
            "proxy_present=${proxyClient != null}",
            "proxy_started=${runCatching { proxyClient?.isStarted == true }.getOrDefault(false)}",
            "tx_delta_bytes=${delta(uidTxBytes(), processTxBaseline)}",
            "rx_delta_bytes=${delta(uidRxBytes(), processRxBaseline)}",
            *protocolFields().toTypedArray()
        )
    }

    fun event(event: String, vararg fields: String) {
        if (!BuildConfig.DEBUG) return
        val suffix = if (fields.isEmpty()) "" else " ${fields.joinToString(" ")}"
        Log.i(TAG, "event=$event$suffix")
    }

    private fun onSocketCreated(socket: Socket) {
        val socketId = socketSequence.incrementAndGet()
        val txBaseline = uidTxBytes()
        val rxBaseline = uidRxBytes()
        event("socket_created", "socket_id=$socketId")
        scheduleSocketProbe(socket, socketId, txBaseline, rxBaseline, 2_000L)
        scheduleSocketProbe(socket, socketId, txBaseline, rxBaseline, 10_000L)
        scheduleSocketProbe(socket, socketId, txBaseline, rxBaseline, 30_000L)
    }

    private fun observeNetwork(context: Context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        if (connectivityManager == null) {
            event("network_observer_failed", "reason=service_unavailable")
            return
        }
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        networkSnapshot("network_available", connectivityManager)
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        networkSnapshot("network_capabilities_changed", connectivityManager)
                    }

                    override fun onLost(network: Network) {
                        networkSnapshot("network_lost", connectivityManager)
                    }
                }
            )
        }.onFailure {
            event("network_observer_failed", "reason=${it.javaClass.simpleName}")
        }
    }

    private fun scheduleRuntimeProbe(delayMillis: Long) {
        mainHandler.postDelayed(
            { runtimeSnapshot("runtime_probe_${delayMillis}ms") },
            delayMillis
        )
    }

    private fun scheduleSocketProbe(
        socket: Socket,
        socketId: Int,
        txBaseline: Long,
        rxBaseline: Long,
        delayMillis: Long
    ) {
        mainHandler.postDelayed(
            {
                val connected = runCatching { socket.isConnected && !socket.isClosed }
                    .getOrDefault(false)
                event(
                    "socket_probe",
                    "socket_id=$socketId",
                    "after_ms=$delayMillis",
                    "connected=$connected",
                    "closed=${runCatching { socket.isClosed }.getOrDefault(true)}",
                    "tx_delta_bytes=${delta(uidTxBytes(), txBaseline)}",
                    "rx_delta_bytes=${delta(uidRxBytes(), rxBaseline)}",
                    *protocolFields().toTypedArray()
                )
            },
            delayMillis
        )
    }

    private fun uidTxBytes(): Long = TrafficStats.getUidTxBytes(Process.myUid())

    private fun uidRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid())

    private fun protocolFields(): List<String> {
        val proxyClient = runCatching { DuntaService.proxyClient }.getOrNull()
        val serverChannel = runCatching {
            proxyClient
                ?.javaClass
                ?.getDeclaredField("serverChannel")
                ?.apply { isAccessible = true }
                ?.get(proxyClient) as? ServerChannel
        }.getOrNull()
        val provider = runCatching {
            serverChannel
                ?.javaClass
                ?.getDeclaredField("provider")
                ?.apply { isAccessible = true }
                ?.get(serverChannel) as? BotProtocolProvider
        }.getOrNull()

        return listOf(
            "backend_channel_present=${serverChannel != null}",
            "backend_connected=${runCatching { serverChannel?.socketIsConnected() == true }.getOrDefault(false)}",
            "backend_registered=${runCatching { (serverChannel?.botId?.get() ?: 0L) > 0L }.getOrDefault(false)}",
            "active_proxy_channels=${runCatching { provider?.channelsSize ?: -1 }.getOrDefault(-1)}",
            "server_status=${runCatching {
                serverChannel
                    ?.javaClass
                    ?.getDeclaredField("channelStatus")
                    ?.apply { isAccessible = true }
                    ?.get(serverChannel)
                    ?.toString() ?: "unknown"
            }.getOrDefault("unknown")}",
            "in_queue=${runCatching { provider?.binQueueSize ?: -1 }.getOrDefault(-1)}",
            "out_queue=${runCatching { provider?.boutQueueSize ?: -1 }.getOrDefault(-1)}"
        )
    }

    private fun delta(current: Long, baseline: Long): Long {
        if (current == TrafficStats.UNSUPPORTED.toLong() ||
            baseline == TrafficStats.UNSUPPORTED.toLong()
        ) {
            return TrafficStats.UNSUPPORTED.toLong()
        }
        return (current - baseline).coerceAtLeast(0L)
    }
}
