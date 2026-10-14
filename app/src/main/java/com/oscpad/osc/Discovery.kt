package com.oscpad.osc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executor

/** An OSC service found via Bonjour / DNS-SD. [host] is null until it has been resolved. */
data class DiscoveredService(
    val name: String,
    val host: String?,
    val port: Int,
    val isSelf: Boolean,
)

/**
 * Zeroconf (Bonjour / DNS-SD) for OSC: browses for `_osc._udp` services that other apps
 * announce, and optionally announces this device so hosts can find it.
 * All state changes happen on the main thread.
 */
class OscDiscovery(context: Context) {

    companion object {
        const val SERVICE_TYPE = "_osc._udp"
    }

    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }

    private val _services = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val services: StateFlow<List<DiscoveredService>> = _services.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Name this device is announced under (may differ from the requested one after conflicts). */
    private val _selfName = MutableStateFlow<String?>(null)
    val selfName: StateFlow<String?> = _selfName.asStateFlow()

    private val found = LinkedHashMap<String, DiscoveredService>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = HashMap<String, Any>() // API 34+ ServiceInfoCallbacks by service name
    private val resolveQueue = ArrayDeque<NsdServiceInfo>() // pre-34: one resolve at a time
    private var resolving = false
    private var registration: NsdManager.RegistrationListener? = null

    // ---- Browsing ---------------------------------------------------------------------------

    fun start() {
        val nsd = nsd ?: run { _error.value = "Network service discovery is not available"; return }
        if (discoveryListener != null) return
        found.clear()
        publish()
        _error.value = null
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                main.post { _searching.value = true }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                main.post { _searching.value = false }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post {
                    _searching.value = false
                    _error.value = "Discovery failed to start (error $errorCode)"
                    discoveryListener = null
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { onFound(info) }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { onLost(info.serviceName) }
            }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            discoveryListener = null
            _error.value = "Discovery failed: ${e.message}"
        }
    }

    fun stop() {
        val nsd = nsd ?: return
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        if (Build.VERSION.SDK_INT >= 34) {
            for (cb in infoCallbacks.values) runCatching { nsd.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) }
        }
        infoCallbacks.clear()
        resolveQueue.clear()
        resolving = false
        _searching.value = false
    }

    private fun onFound(info: NsdServiceInfo) {
        if (discoveryListener == null) return
        val name = info.serviceName
        if (name !in found) {
            found[name] = DiscoveredService(name, host = null, port = 0, isSelf = name == _selfName.value)
            publish()
        }
        resolve(info)
    }

    private fun onLost(name: String) {
        if (found.remove(name) != null) publish()
        if (Build.VERSION.SDK_INT >= 34) {
            infoCallbacks.remove(name)?.let { cb ->
                runCatching { nsd?.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) }
            }
        }
    }

    private fun onResolved(name: String, addresses: List<InetAddress>, port: Int) {
        if (discoveryListener == null) return
        val host = (addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull())?.hostAddress
        found[name] = DiscoveredService(name, host, port, isSelf = name == _selfName.value)
        publish()
    }

    private fun resolve(info: NsdServiceInfo) {
        val nsd = nsd ?: return
        if (Build.VERSION.SDK_INT >= 34) {
            if (info.serviceName in infoCallbacks) return
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(serviceInfo: NsdServiceInfo) =
                    onResolved(serviceInfo.serviceName, serviceInfo.hostAddresses, serviceInfo.port)
                override fun onServiceLost() = onLost(info.serviceName)
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            infoCallbacks[info.serviceName] = cb
            runCatching { nsd.registerServiceInfoCallback(info, mainExecutor, cb) }
        } else {
            resolveQueue.addLast(info)
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val nsd = nsd ?: return
        if (resolving) return
        val info = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                main.post {
                    resolving = false
                    resolveNext()
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                main.post {
                    resolving = false
                    onResolved(serviceInfo.serviceName, listOfNotNull(serviceInfo.host), serviceInfo.port)
                    resolveNext()
                }
            }
        })
    }

    private fun publish() {
        _services.value = found.values.sortedWith(compareBy({ it.isSelf }, { it.name.lowercase() }))
    }

    // ---- Announcing -------------------------------------------------------------------------

    fun announce(name: String, port: Int) {
        val nsd = nsd ?: return
        unannounce()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                main.post {
                    _selfName.value = serviceInfo.serviceName
                    found.replaceAll { _, s -> s.copy(isSelf = s.name == serviceInfo.serviceName) }
                    publish()
                }
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unannounce() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        registration = null
        _selfName.value = null
    }

    fun release() {
        stop()
        unannounce()
    }
}
