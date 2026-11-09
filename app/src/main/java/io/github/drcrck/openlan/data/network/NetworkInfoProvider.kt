package io.github.drcrck.openlan.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import io.github.drcrck.openlan.scanner.Ipv4
import io.github.drcrck.openlan.scanner.MacAddress
import java.net.Inet4Address

/** Fotografia della rete locale corrente. */
data class NetworkInfo(
    val ssid: String?,
    val bssid: String?,
    val ipAddress: String,
    val prefixLength: Int,
    val gateway: String?,
    val dnsServers: List<String>,
    val dhcpServer: String?,
    val rssi: Int?,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val isWifi: Boolean,
) {
    val subnet: String get() = Ipv4.subnetLabel(ipAddress, prefixLength)


    /** Chiave stabile della rete nel database: BSSID se noto, altrimenti gateway + subnet. */
    val key: String get() = bssid ?: "net:${gateway ?: "?"}@$subnet"
}

class NetworkInfoProvider(context: Context) {

    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    /** Rete Wi-Fi (o Ethernet) corrente; null se il telefono non è su una LAN. */
    fun current(): NetworkInfo? {
        val network = findLocalNetwork() ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val lp = cm.getLinkProperties(network) ?: return null

        val link = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val gateway = lp.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress
        val dns = lp.dnsServers.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress }

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        @Suppress("DEPRECATION")
        val wi = if (isWifi) wifi?.connectionInfo else null

        // Server DHCP: API 30+ da LinkProperties, altrimenti da DhcpInfo (intero little-endian)
        val dhcp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.dhcpServerAddress?.hostAddress
        } else null
        @Suppress("DEPRECATION")
        val dhcpLegacy = if (dhcp == null && isWifi) wifi?.dhcpInfo?.serverAddress?.takeIf { it != 0 }?.let {
            "${it and 255}.${(it ushr 8) and 255}.${(it ushr 16) and 255}.${(it ushr 24) and 255}"
        } else null

        return NetworkInfo(
            ssid = wi?.ssid?.removeSurrounding("\"")?.takeUnless { it.isBlank() || it == "<unknown ssid>" },
            bssid = wi?.bssid?.let { MacAddress.normalize(it) }?.takeUnless { it == "02:00:00:00:00:00" },
            ipAddress = link.address.hostAddress ?: return null,
            prefixLength = link.prefixLength,
            gateway = gateway,
            dnsServers = dns,
            dhcpServer = dhcp ?: dhcpLegacy,
            rssi = wi?.rssi?.takeIf { it > -127 },
            linkSpeedMbps = wi?.linkSpeed?.takeIf { it > 0 },
            frequencyMhz = wi?.frequency?.takeIf { it > 0 },
            isWifi = isWifi,
        )
    }

    private fun findLocalNetwork(): Network? {
        fun isLan(n: Network): Boolean {
            val c = cm.getNetworkCapabilities(n) ?: return false
            return c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        cm.activeNetwork?.let { if (isLan(it)) return it }
        // Il Wi-Fi può non essere la rete predefinita (es. senza Internet): lo cerchiamo comunque
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { isLan(it) }
    }
}
