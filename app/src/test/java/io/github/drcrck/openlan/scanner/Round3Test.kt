package io.github.drcrck.openlan.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Casi tipici di una rete reale (nomi di esempio). */
class ClassifierRealCasesTest {
    private fun c(i: ClassifierInput) = DeviceClassifier.classify(i)

    @Test fun synology_xserve_e_nas() {
        assertEquals(DeviceType.NAS, c(ClassifierInput(
            services = setOf("_afpovertcp._tcp", "_device-info._tcp", "_smb._tcp"), model = "Xserve",
            netbiosName = "RS3621", names = listOf("RS3621"))))
    }

    @Test fun qnap_da_servizi_e_nas() {
        assertEquals(DeviceType.NAS, c(ClassifierInput(
            services = setOf("_qdiscover._tcp", "_smb._tcp", "_workstation._tcp"), netbiosName = "NAS01", names = listOf("NAS01"))))
    }

    @Test fun idrac_e_server() {
        assertEquals(DeviceType.SERVER, c(ClassifierInput(names = listOf("idrac-ABC1234"))))
        assertEquals(DeviceType.SERVER, c(ClassifierInput(netbiosName = "SRVFILE", names = listOf("SRVFILE"))))
        assertEquals(DeviceType.SERVER, c(ClassifierInput(netbiosName = "PVE", names = listOf("PVE"))))
    }

    @Test fun pc_resta_computer() {
        assertEquals(DeviceType.COMPUTER, c(ClassifierInput(netbiosName = "PC-UFFICIO", names = listOf("PC-UFFICIO"))))
    }

    @Test fun synology_da_server_ssdp() {
        assertEquals(DeviceType.NAS, c(ClassifierInput(upnpServer = "Synology/DSM/192.168.1.20")))
    }
}

class PingTest {
    @Test fun parsing() {
        assertEquals(3.21, PingParser.parseTime("64 bytes from 192.168.1.2: icmp_seq=1 ttl=128 time=3.21 ms")!!, 0.001)
        assertNull(PingParser.parseTime("PING 192.168.1.9 (192.168.1.9) 56(84) bytes of data.\n\n--- statistics ---"))
    }

    @Test fun statistiche() {
        val s = PingStats().add(2.0).add(null).add(4.0)
        assertEquals(3, s.sent)
        assertEquals(2, s.received)
        assertEquals(3.0, s.avg!!, 0.001)
        assertEquals(2.0, s.min!!, 0.001)
        assertEquals(4.0, s.max!!, 0.001)
        assertEquals(33.3, s.lossPercent, 0.1)
    }
}
