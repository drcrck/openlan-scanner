package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Porta TCP aperta. [isHttp] = ha risposto come server web (anche su porte non standard). */
data class OpenPort(val port: Int, val service: String?, val isHttp: Boolean = false, val isTls: Boolean = false)

/** TCP connect scan: nessun privilegio richiesto, ~100 connessioni in parallelo. */
class PortScanner(
    private val timeoutMs: Int = 400,
    private val concurrency: Int = 128,
) {
    private val io = Dispatchers.IO.limitedParallelism(concurrency)

    suspend fun scan(ip: String, ports: List<Int>, onProgress: (done: Int, total: Int, open: List<OpenPort>) -> Unit): List<OpenPort> =
        coroutineScope {
            val sem = Semaphore(concurrency)
            val done = AtomicInteger(0)
            val open = java.util.concurrent.ConcurrentSkipListMap<Int, OpenPort>()
            ports.map { port ->
                async(io) {
                    sem.withPermit {
                        if (isOpen(ip, port)) {
                            open[port] = identify(ip, port)
                        }
                    }
                    onProgress(done.incrementAndGet(), ports.size, open.values.toList())
                }
            }.awaitAll()
            open.values.toList()
        }

    private fun isOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs) }
        true
    } catch (_: Exception) {
        false
    }

    /** Porte note → nome servizio; le altre si sondano con una richiesta HTTP per scoprire interfacce web. */
    private suspend fun identify(ip: String, port: Int): OpenPort {
        val known = KnownPorts.byPort[port]
        if (known != null) return OpenPort(port, known.name, known.kind == PortKind.HTTP, known.kind == PortKind.HTTPS)
        val http = withContext(Dispatchers.IO) { looksLikeHttp(ip, port) }
        return OpenPort(port, if (http) "web" else null, isHttp = http)
    }

    private fun looksLikeHttp(ip: String, port: Int): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            s.soTimeout = 1200
            s.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray())
            val buf = ByteArray(5)
            val n = s.getInputStream().read(buf)
            n == 5 && String(buf) == "HTTP/"
        }
    } catch (_: Exception) {
        false
    }
}

enum class PortKind { HTTP, HTTPS, SSH, TELNET, FTP, SMB, RDP, VNC, RTSP, OTHER }

data class KnownPort(val port: Int, val name: String, val kind: PortKind = PortKind.OTHER)

object KnownPorts {
    val list: List<KnownPort> = listOf(
        KnownPort(20, "ftp-data"), KnownPort(21, "FTP", PortKind.FTP), KnownPort(22, "SSH", PortKind.SSH),
        KnownPort(23, "Telnet", PortKind.TELNET), KnownPort(25, "SMTP"), KnownPort(53, "DNS"),
        KnownPort(80, "HTTP", PortKind.HTTP), KnownPort(81, "HTTP alt", PortKind.HTTP), KnownPort(88, "Kerberos"),
        KnownPort(110, "POP3"), KnownPort(111, "RPC"), KnownPort(135, "MS RPC"), KnownPort(139, "NetBIOS/SMB", PortKind.SMB),
        KnownPort(143, "IMAP"), KnownPort(161, "SNMP"), KnownPort(389, "LDAP"), KnownPort(443, "HTTPS", PortKind.HTTPS),
        KnownPort(445, "SMB", PortKind.SMB), KnownPort(465, "SMTPS"), KnownPort(515, "LPD (stampa)"),
        KnownPort(548, "AFP"), KnownPort(554, "RTSP", PortKind.RTSP), KnownPort(587, "SMTP submission"),
        KnownPort(631, "IPP / CUPS", PortKind.HTTP), KnownPort(636, "LDAPS"), KnownPort(873, "rsync"),
        KnownPort(993, "IMAPS"), KnownPort(995, "POP3S"), KnownPort(1080, "SOCKS"), KnownPort(1194, "OpenVPN"),
        KnownPort(1400, "Sonos", PortKind.HTTP), KnownPort(1433, "SQL Server"), KnownPort(1521, "Oracle"),
        KnownPort(1723, "PPTP"), KnownPort(1883, "MQTT"), KnownPort(1900, "UPnP"), KnownPort(2049, "NFS"),
        KnownPort(2375, "Docker"), KnownPort(3000, "HTTP (dev)", PortKind.HTTP), KnownPort(3306, "MySQL"),
        KnownPort(3389, "Desktop remoto", PortKind.RDP), KnownPort(3483, "Squeezebox"), KnownPort(4443, "HTTPS alt", PortKind.HTTPS),
        KnownPort(5000, "HTTP (Synology/UPnP)", PortKind.HTTP), KnownPort(5001, "HTTPS (Synology)", PortKind.HTTPS),
        KnownPort(5060, "SIP"), KnownPort(5353, "mDNS"), KnownPort(5357, "WSD"), KnownPort(5432, "PostgreSQL"),
        KnownPort(5555, "ADB"), KnownPort(5900, "VNC", PortKind.VNC), KnownPort(5901, "VNC :1", PortKind.VNC),
        KnownPort(6379, "Redis"), KnownPort(6443, "Kubernetes API"), KnownPort(7000, "AirPlay"),
        KnownPort(7547, "TR-069"), KnownPort(8000, "HTTP alt", PortKind.HTTP), KnownPort(8008, "HTTP (Chromecast)", PortKind.HTTP),
        KnownPort(8009, "Cast"), KnownPort(8080, "HTTP proxy/alt", PortKind.HTTP), KnownPort(8081, "HTTP alt", PortKind.HTTP),
        KnownPort(8083, "HTTP alt", PortKind.HTTP), KnownPort(8086, "InfluxDB", PortKind.HTTP), KnownPort(8088, "HTTP alt", PortKind.HTTP),
        KnownPort(8096, "Jellyfin", PortKind.HTTP), KnownPort(8123, "Home Assistant", PortKind.HTTP),
        KnownPort(8291, "MikroTik Winbox"), KnownPort(8443, "HTTPS alt", PortKind.HTTPS), KnownPort(8448, "Matrix", PortKind.HTTPS),
        KnownPort(8554, "RTSP alt", PortKind.RTSP), KnownPort(8843, "HTTPS (UniFi)", PortKind.HTTPS),
        KnownPort(8880, "HTTP alt", PortKind.HTTP), KnownPort(8888, "HTTP alt", PortKind.HTTP),
        KnownPort(9000, "HTTP (Portainer)", PortKind.HTTP), KnownPort(9001, "HTTP alt", PortKind.HTTP),
        KnownPort(9090, "HTTP (Cockpit/Prometheus)", PortKind.HTTPS), KnownPort(9100, "Stampa RAW (JetDirect)"),
        KnownPort(9200, "Elasticsearch", PortKind.HTTP), KnownPort(9443, "HTTPS (Portainer)", PortKind.HTTPS),
        KnownPort(10000, "Webmin", PortKind.HTTPS), KnownPort(32400, "Plex", PortKind.HTTP),
        KnownPort(49152, "UPnP"), KnownPort(51820, "WireGuard"), KnownPort(62078, "iPhone sync"),
    )

    val byPort: Map<Int, KnownPort> = list.associateBy { it.port }

    /** Elenco veloce: porte note + porte web frequenti. */
    val common: List<Int> = list.map { it.port }.distinct().sorted()

    /** Prime 1024 porte + elenco noto. */
    val extended: List<Int> = ((1..1024) + common).distinct().sorted()
}
