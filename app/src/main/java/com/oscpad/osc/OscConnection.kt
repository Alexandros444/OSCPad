package com.oscpad.osc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicLong

data class ConnectionSettings(
    val host: String = "192.168.1.100",
    val sendPort: Int = 8000,
    val receivePort: Int = 9000,
)

data class ConnectionStatus(
    val targetResolved: Boolean = false,
    val listeningPort: Int? = null,
    val error: String? = null,
)

/**
 * One UDP socket bound to the receive port, used for both directions. Sending from the
 * listening port lets hosts that reply to the sender's address reach us without extra setup.
 */
class OscConnection(private val onMessage: (OscMessage) -> Unit) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outgoing = Channel<ByteArray>(capacity = 1024, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val configLock = Mutex()

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var target: InetSocketAddress? = null
    private var receiveJob: Job? = null

    val txCount = AtomicLong()
    val rxCount = AtomicLong()

    private val _status = MutableStateFlow(ConnectionStatus())
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    init {
        scope.launch {
            for (data in outgoing) {
                val s = socket ?: continue
                val t = target ?: continue
                try {
                    s.send(DatagramPacket(data, data.size, t))
                    txCount.incrementAndGet()
                } catch (e: IOException) {
                    _status.update { it.copy(error = "Send failed: ${e.message}") }
                }
            }
        }
    }

    fun configure(settings: ConnectionSettings) {
        scope.launch {
            configLock.withLock {
                socket?.close() // unblocks the receive loop
                receiveJob?.join()

                var error: String? = null
                target = try {
                    InetSocketAddress(InetAddress.getByName(settings.host.trim()), settings.sendPort)
                } catch (e: Exception) {
                    error = "Cannot resolve host \"${settings.host}\""
                    null
                }

                val s = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(settings.receivePort))
                    }
                } catch (e: Exception) {
                    error = "Receive port ${settings.receivePort} unavailable (${e.message}); feedback disabled"
                    DatagramSocket()
                }
                s.broadcast = true
                socket = s
                _status.value = ConnectionStatus(
                    targetResolved = target != null,
                    listeningPort = if (s.localPort == settings.receivePort) s.localPort else null,
                    error = error,
                )
                receiveJob = scope.launch { receiveLoop(s) }
            }
        }
    }

    fun send(address: String, args: List<Any?>) {
        outgoing.trySend(OscCodec.encodeMessage(address, args))
    }

    fun close() {
        socket?.close()
        outgoing.close()
        scope.cancel()
    }

    private fun receiveLoop(s: DatagramSocket) {
        val buffer = ByteArray(65_535)
        val packet = DatagramPacket(buffer, buffer.size)
        while (!s.isClosed) {
            packet.length = buffer.size
            try {
                s.receive(packet)
            } catch (_: IOException) {
                break
            }
            rxCount.incrementAndGet()
            for (message in OscCodec.decode(buffer, packet.length)) onMessage(message)
        }
    }
}

/** IPv4 addresses of this device, shown in settings so the user can configure the host side. */
fun localIpv4Addresses(): List<String> = try {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
} catch (_: Exception) {
    emptyList()
}
