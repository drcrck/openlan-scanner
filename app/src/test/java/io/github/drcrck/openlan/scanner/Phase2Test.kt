package io.github.drcrck.openlan.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsCodecTest {

    /** Costruisce una risposta mDNS con PTR, SRV, TXT, A (nomi non compressi + un puntatore). */
    private fun response(): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) { out.write(v ushr 8); out.write(v and 0xFF) }
        fun name(n: String) { n.split(".").forEach { val b = it.toByteArray(); out.write(b.size); out.write(b) }; out.write(0) }
        fun rr(n: String, type: Int, rdata: ByteArray) { name(n); u16(type); u16(0x8001); u16(0); u16(120); u16(rdata.size); out.write(rdata) }
        fun nameBytes(n: String) = ByteArrayOutputStream().also { o -> n.split(".").forEach { val b = it.toByteArray(); o.write(b.size); o.write(b) }; o.write(0) }.toByteArray()

        u16(0); u16(0x8400); u16(0); u16(4); u16(0); u16(0)
        rr("_airplay._tcp.local", DnsCodec.TYPE_PTR, nameBytes("TV Sala._airplay._tcp.local"))
        rr("TV Sala._airplay._tcp.local", DnsCodec.TYPE_SRV, byteArrayOf(0, 0, 0, 0, 0x1B, 0x58) + nameBytes("AppleTV.local"))
        val txt = listOf("deviceid=58:55:CA:12:34:56", "model=AppleTV11,1")
            .flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() }.toByteArray()
        rr("TV Sala._airplay._tcp.local", DnsCodec.TYPE_TXT, txt)
        rr("AppleTV.local", DnsCodec.TYPE_A, byteArrayOf(192.toByte(), 168.toByte(), 1, 50))
        return out.toByteArray()
    }

    @Test fun parsing_e_aggregazione() {
        val records = DnsCodec.parseRecords(response())
        assertEquals(4, records.size)

        val agg = MdnsAggregator()
        agg.add("192.168.1.99", records) // mittente diverso: vince il record A
        val info = agg.result()["192.168.1.50"]!!
        assertEquals("AppleTV", info.hostname)
        assertEquals(setOf("_airplay._tcp"), info.services)
        assertEquals("TV Sala", info.friendlyName)
        assertEquals("AppleTV11,1", info.model)
        assertEquals("58:55:CA:12:34:56", MacResolver.resolve(info.macCandidates())?.mac)
        assertEquals(DeviceType.TV, DeviceClassifier.classify(ClassifierInput(services = info.services, model = info.model)))
    }

    @Test fun query_ben_formata() {
        val q = DnsCodec.buildQuery(1, listOf("_ipp._tcp.local" to DnsCodec.TYPE_PTR))
        assertEquals(12 + 17 + 4, q.size)
    }

    @Test fun nomi_inversi() {
        assertEquals("10.1.168.192.in-addr.arpa", DnsCodec.reverseName("192.168.1.10"))
        assertEquals("192.168.1.10", DnsCodec.ipFromReverseName("10.1.168.192.in-addr.arpa"))
    }

    @Test fun mac_da_nomi_istanza() {
        val info = MdnsHostInfo(
            "1.2.3.4", null, setOf("_raop._tcp", "_workstation._tcp"),
            mapOf("_raop._tcp" to "A4D1D2001122@Salotto", "_workstation._tcp" to "nas [00:11:32:aa:bb:cc]"),
            emptyMap(),
        )
        val macs = info.macCandidates().map { MacAddress.normalize(it.mac) }
        assertTrue("A4:D1:D2:00:11:22" in macs)
        assertTrue("00:11:32:AA:BB:CC" in macs)
        assertEquals("Salotto", info.friendlyName)
    }
}

class SsdpParserTest {
    @Test fun udn_versione_1_e_mac() {
        assertEquals("00:1A:2B:3C:4D:5E" to MacSource.SSDP, SsdpParser.macFromUdn("uuid:2fac1234-31f8-11b4-a222-001a2b3c4d5e"))
    }

    @Test fun udn_versione_4_scartato() {
        assertNull(SsdpParser.macFromUdn("uuid:9b2a6f0e-1c3d-4a5b-8c7d-0123456789ab"))
    }

    @Test fun descrizione_xml() {
        val xml = """<root><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>[TV] Samsung Q60</friendlyName><manufacturer>Samsung Electronics</manufacturer>
            <modelName>QE55Q60</modelName><UDN>uuid:abc</UDN></device></root>"""
        val d = SsdpParser.parseDescription(SsdpDevice("1.2.3.4", null, null), xml)
        assertEquals("[TV] Samsung Q60", d.friendlyName)
        assertEquals("QE55Q60", d.modelName)
        assertEquals(
            DeviceType.TV,
            DeviceClassifier.classify(ClassifierInput(manufacturer = d.manufacturer, upnpDeviceType = d.deviceType)),
        )
    }

    @Test fun intestazioni() {
        val h = SsdpParser.parseHeaders("HTTP/1.1 200 OK\r\nLOCATION: http://1.2.3.4:49152/desc.xml\r\nst: upnp:rootdevice\r\n\r\n")
        assertEquals("http://1.2.3.4:49152/desc.xml", h["LOCATION"])
        assertEquals("upnp:rootdevice", h["ST"])
    }
}

class TracerouteTest {
    @Test fun ttl_scaduto() {
        val (ip, reached, _) = TracerouteParser.parse(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\nFrom 192.168.1.1: icmp_seq=1 Time to live exceeded\n"
        )
        assertEquals("192.168.1.1", ip)
        assertFalse(reached)
    }

    @Test fun destinazione_raggiunta() {
        val (ip, reached, time) = TracerouteParser.parse(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.4 ms\n"
        )
        assertEquals("8.8.8.8", ip)
        assertTrue(reached)
        assertEquals(12.4, time!!, 0.01)
    }

    @Test fun nessuna_risposta() {
        val (ip, reached, _) = TracerouteParser.parse("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n\n--- 8.8.8.8 ping statistics ---\n")
        assertNull(ip)
        assertFalse(reached)
    }

    @Test fun zone() {
        assertEquals(HopZone.LAN, TracerouteTool.zoneOf("192.168.1.1"))
        assertEquals(HopZone.CGNAT, TracerouteTool.zoneOf("100.70.1.1"))
        assertEquals(HopZone.INTERNET, TracerouteTool.zoneOf("8.8.8.8"))
    }
}

class ClassifierTest {
    @Test fun regole_principali() {
        assertEquals(DeviceType.ROUTER, DeviceClassifier.classify(ClassifierInput(isGateway = true)))
        assertEquals(DeviceType.PRINTER, DeviceClassifier.classify(ClassifierInput(services = setOf("_ipp._tcp"))))
        assertEquals(DeviceType.COMPUTER, DeviceClassifier.classify(ClassifierInput(netbiosName = "PC-UFFICIO")))
        assertEquals(DeviceType.IOT, DeviceClassifier.classify(ClassifierInput(vendor = "Espressif Inc.")))
        assertEquals(DeviceType.UNKNOWN, DeviceClassifier.classify(ClassifierInput()))
    }
}
