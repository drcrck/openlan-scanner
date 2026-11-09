package io.github.drcrck.openlan.data.repository

import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.scanner.DiscoveredHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceMatcherTest {

    private fun dev(id: Long, ip: String, mac: String? = null, nb: String? = null, host: String? = null, self: Boolean = false) =
        DeviceEntity(id = id, networkKey = "n", mac = mac, netbiosName = nb, hostname = host, lastIp = ip,
            isSelf = self, firstSeen = 0, lastSeen = 0)

    private fun host(ip: String, mac: String? = null, nb: String? = null, hostname: String? = null, self: Boolean = false) =
        DiscoveredHost(ip, self, false, mac, null, null, nb, hostname)

    @Test fun mac_ha_la_precedenza() {
        val known = listOf(dev(1, "192.168.1.10"), dev(2, "192.168.1.20", mac = "00:11:22:33:44:55"))
        val (d, k) = DeviceMatcher.match(host("192.168.1.10", mac = "00:11:22:33:44:55"), known)
        assertEquals(2L, d?.id)
        assertEquals(MatchKind.MAC, k)
    }

    @Test fun nome_netbios_se_ip_cambiato() {
        val known = listOf(dev(1, "192.168.1.10", nb = "PC-SALOTTO"))
        val (d, k) = DeviceMatcher.match(host("192.168.1.55", nb = "pc-salotto"), known)
        assertEquals(1L, d?.id)
        assertEquals(MatchKind.NETBIOS, k)
    }

    @Test fun solo_ip_e_incerto() {
        val known = listOf(dev(1, "192.168.1.10"))
        val (_, k) = DeviceMatcher.match(host("192.168.1.10"), known)
        assertEquals(MatchKind.IP, k)
    }

    @Test fun mac_diversi_non_si_associano_per_ip() {
        val known = listOf(dev(1, "192.168.1.10", mac = "00:11:22:33:44:55"))
        val (d, k) = DeviceMatcher.match(host("192.168.1.10", mac = "00:AA:BB:CC:DD:EE"), known)
        assertNull(d)
        assertEquals(MatchKind.NEW, k)
    }

    @Test fun telefono_riconosciuto_come_self() {
        val known = listOf(dev(1, "192.168.1.10", self = true), dev(2, "192.168.1.11"))
        val (d, k) = DeviceMatcher.match(host("192.168.1.11", self = true), known)
        assertEquals(1L, d?.id)
        assertEquals(MatchKind.SELF, k)
    }
}
