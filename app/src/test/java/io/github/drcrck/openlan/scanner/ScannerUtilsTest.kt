package io.github.drcrck.openlan.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacAddressTest {
    @Test fun normalizza_formati_comuni() {
        assertEquals("AA:BB:CC:DD:EE:FF", MacAddress.normalize("aa-bb-cc-dd-ee-ff"))
        assertEquals("AA:BB:CC:DD:EE:FF", MacAddress.normalize("aabbccddeeff"))
        // AA ha il bit "locally administered" ma non quello multicast: valido
        assertTrue(MacAddress.isLocallyAdministered("AA:BB:CC:DD:EE:FF"))
    }

    @Test fun scarta_mac_non_utilizzabili() {
        assertNull(MacAddress.normalize("00:00:00:00:00:00"))
        assertNull(MacAddress.normalize("FF:FF:FF:FF:FF:FF"))
        assertNull(MacAddress.normalize("01:00:5E:00:00:FB")) // multicast
        assertNull(MacAddress.normalize("123"))
    }

    @Test fun mac_globale() {
        assertFalse(MacAddress.isLocallyAdministered("F4:F5:D8:11:22:33"))
    }
}

class Ipv4Test {
    @Test fun subnet_24() {
        val r = Ipv4.hostsToScan("192.168.1.37", 24)
        assertEquals(254, r.hosts.size)
        assertEquals("192.168.1.1", r.hosts.first())
        assertEquals("192.168.1.254", r.hosts.last())
        assertFalse(r.limited)
    }

    @Test fun subnet_grande_ridotta_a_24() {
        val r = Ipv4.hostsToScan("10.1.2.3", 16)
        assertTrue(r.limited)
        assertEquals(254, r.hosts.size)
        assertEquals("10.1.2.1", r.hosts.first())
    }

    @Test fun subnet_22() {
        val r = Ipv4.hostsToScan("192.168.5.10", 22)
        assertEquals(1022, r.hosts.size)
        assertEquals("192.168.4.1", r.hosts.first())
        assertEquals("192.168.7.254", r.hosts.last())
        assertEquals("192.168.4.0/22", Ipv4.subnetLabel("192.168.5.10", 22))
    }
}

class NetbiosCodecTest {
    @Test fun query_nbstat() {
        val q = NetbiosCodec.buildNbstatQuery(0x1234)
        assertEquals(50, q.size)
        assertEquals(0x12, q[0].toInt())
        assertEquals(0x34, q[1].toInt())
        assertEquals('C'.code, q[13].toInt())
        assertEquals('K'.code, q[14].toInt())
        assertEquals(0x21, q[47].toInt())
    }

    @Test fun parsing_risposta() {
        val resp = buildResponse(
            txId = 7,
            names = listOf(
                Triple("WORKGROUP", 0x00, true),
                Triple("PC-UFFICIO", 0x00, false),
                Triple("PC-UFFICIO", 0x20, false),
            ),
            mac = byteArrayOf(0x00, 0x1A.toByte(), 0x2B, 0x3C, 0x4D, 0x5E),
        )
        val p = NetbiosCodec.parseNbstatResponse(resp)!!
        assertEquals(7, p.transactionId)
        assertEquals("PC-UFFICIO", p.name)
        assertEquals("WORKGROUP", p.workgroup)
        assertEquals("00:1A:2B:3C:4D:5E", p.mac)
    }

    @Test fun samba_mac_a_zero_scartato() {
        val resp = buildResponse(1, listOf(Triple("NAS", 0x00, false)), ByteArray(6))
        assertNull(NetbiosCodec.parseNbstatResponse(resp)!!.mac)
    }

    private fun buildResponse(txId: Int, names: List<Triple<String, Int, Boolean>>, mac: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf((txId ushr 8).toByte(), txId.toByte(), 0x84.toByte(), 0x00, 0, 0, 0, 1, 0, 0, 0, 0))
        val q = NetbiosCodec.buildNbstatQuery(0)
        out.write(q, 12, 34) // nome codificato
        out.write(byteArrayOf(0x00, 0x21, 0x00, 0x01, 0, 0, 0, 0))
        val rdLen = 1 + names.size * 18 + 6
        out.write(byteArrayOf((rdLen ushr 8).toByte(), rdLen.toByte()))
        out.write(names.size)
        for ((n, suffix, group) in names) {
            out.write(n.padEnd(15).toByteArray(Charsets.US_ASCII))
            out.write(suffix)
            out.write(if (group) 0x84 else 0x04)
            out.write(0x00)
        }
        out.write(mac)
        return out.toByteArray()
    }
}

class MacResolverTest {
    @Test fun preferisce_fonte_piu_affidabile() {
        val r = MacResolver.resolve(
            listOf(
                MacCandidate("AA:AA:AA:AA:AA:AA", MacSource.BSSID),
                MacCandidate("00:11:22:33:44:55", MacSource.NETBIOS),
            )
        )
        assertEquals("00:11:22:33:44:55", r?.mac)
        assertEquals(MacSource.NETBIOS, r?.source)
    }

    @Test fun ignora_mac_non_validi() {
        assertNull(MacResolver.resolve(listOf(MacCandidate("00:00:00:00:00:00", MacSource.NETBIOS))))
    }
}
