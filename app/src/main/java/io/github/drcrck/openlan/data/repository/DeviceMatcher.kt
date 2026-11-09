package io.github.drcrck.openlan.data.repository

import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.scanner.DiscoveredHost

/** Come un host della scansione è stato associato a un dispositivo già noto. */
enum class MatchKind {
    SELF, MAC, UDN, NETBIOS, MDNS, HOSTNAME,
    /** Solo per IP: incerto, il DHCP può aver riassegnato l'indirizzo. */
    IP,
    NEW,
}

/**
 * Riconosce un host tra i dispositivi già noti della rete.
 * Priorità: questo telefono → MAC → UDN UPnP → nome NetBIOS → nome mDNS → hostname → IP.
 * Se host e candidato hanno entrambi un MAC e sono diversi, non si associano per nome o IP.
 */
object DeviceMatcher {

    fun match(host: DiscoveredHost, known: List<DeviceEntity>): Pair<DeviceEntity?, MatchKind> {
        if (host.isSelf) {
            known.firstOrNull { it.isSelf }?.let { return it to MatchKind.SELF }
            return null to MatchKind.NEW
        }
        val candidates = known.filter { !it.isSelf }

        host.mac?.let { mac ->
            candidates.firstOrNull { it.mac == mac }?.let { return it to MatchKind.MAC }
        }

        val compatible = candidates.filter { host.mac == null || it.mac == null || it.mac == host.mac }

        fun byName(value: String?, kind: MatchKind, field: (DeviceEntity) -> String?): Pair<DeviceEntity, MatchKind>? =
            value?.let { v -> compatible.firstOrNull { field(it).equals(v, ignoreCase = true) }?.let { it to kind } }

        byName(host.ssdpUdn, MatchKind.UDN) { it.ssdpUdn }?.let { return it }
        byName(host.netbiosName, MatchKind.NETBIOS) { it.netbiosName }?.let { return it }
        byName(host.mdnsName, MatchKind.MDNS) { it.mdnsName }?.let { return it }
        byName(host.hostname, MatchKind.HOSTNAME) { it.hostname }?.let { return it }
        compatible.firstOrNull { it.lastIp == host.ip }?.let { return it to MatchKind.IP }

        return null to MatchKind.NEW
    }
}
