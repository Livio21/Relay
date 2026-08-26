package dev.relay.music.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap

/**
 * NSD service types Relay advertises while a LAN session is open. One type per mode so a
 * guest browsing for "sync" never stumbles into a play-together session.
 */
object LanServiceTypes {
    const val SYNC = "_relay-sync._tcp."
    const val PLAY = "_relay-play._tcp."

    fun forMode(mode: LanPairingLink.Mode): String = when (mode) {
        LanPairingLink.Mode.SYNC -> SYNC
        LanPairingLink.Mode.PLAY -> PLAY
    }
}

/**
 * Advertises this device's listening port on the local network while a hosted session is
 * active. Registration is best-effort: discovery failures never block manual HOST:PORT entry.
 */
class LanAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(mode: LanPairingLink.Mode, port: Int, serviceName: String) {
        unregister()
        if (nsd == null) return
        val info = NsdServiceInfo().apply {
            setServiceName(serviceName.take(63))
            setServiceType(LanServiceTypes.forMode(mode))
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unregister() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { nsd?.unregisterService(listener) }
    }
}

/**
 * Browses the network for Relay sessions of one mode. Updates arrive marshalled to the main
 * thread; [stop] must be called when the user leaves the flow so no listener leaks.
 */
class LanBrowser(context: Context, private val onUpdate: (List<DiscoveredLanHost>) -> Unit) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val found = ConcurrentHashMap<String, Found>()
    @Volatile
    private var mode: LanPairingLink.Mode? = null
    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun start(newMode: LanPairingLink.Mode) {
        stop()
        if (nsd == null) {
            onUpdate(emptyList())
            return
        }
        mode = newMode
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = publish()

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
                main.post { onUpdate(emptyList()) }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                found[serviceInfo.serviceName] = Found(serviceInfo.serviceName, null, 0)
                main.post { publish() }
                nsd.resolveService(
                    serviceInfo,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            val port = info.port.takeIf { it in 1..65535 } ?: return
                            found[info.serviceName] = Found(info.serviceName, host, port)
                            main.post { publish() }
                        }
                    },
                )
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                main.post { publish() }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        discoveryListener = listener
        runCatching { nsd.discoverServices(LanServiceTypes.forMode(newMode), NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        mode = null
        found.clear()
        runCatching { nsd.stopServiceDiscovery(listener) }
    }

    /** Only resolved hosts surface — an unresolved name has nothing joinable yet. */
    private fun publish() {
        val currentMode = mode ?: return
        onUpdate(
            found.values.mapNotNull { entry ->
                if (entry.host.isNullOrBlank()) return@mapNotNull null
                DiscoveredLanHost(entry.name, entry.host, entry.port, currentMode)
            }.sortedBy { it.name },
        )
    }

    private data class Found(val name: String, val host: String?, val port: Int)
}
