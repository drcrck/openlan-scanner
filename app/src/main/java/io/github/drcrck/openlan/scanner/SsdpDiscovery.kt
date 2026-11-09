package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL

/** Dispositivo UPnP: intestazioni della risposta SSDP + descrizione XML. */
data class SsdpDevice(
    val ip: String,
    val location: String?,
    val server: String?,
    val friendlyName: String? = null,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelDescription: String? = null,
    val deviceType: String? = null,
    val udn: String? = null,
    val macAddress: String? = null,
) {
    /** MAC esplicito nell'XML, oppure dedotto dall'UDN (vedi [SsdpParser.macFromUdn]). */
    fun macCandidates(): List<MacCandidate> = buildList {
        macAddress?.let { add(MacCandidate(it, MacSource.SSDP)) }
        udn?.let { SsdpParser.macFromUdn(it) }?.let { add(MacCandidate(it.first, it.second)) }
    }
}

object SsdpParser {

    fun parseHeaders(response: String): Map<String, String> =
        response.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val i = line.indexOf(':')
                if (i <= 0) null else line.substring(0, i).trim().uppercase() to line.substring(i + 1).trim()
            }
            .toMap()

    /** Primo valore di un tag XML (descrizione UPnP); niente parser DOM, basta un'espressione regolare. */
    fun tag(xml: String, name: String): String? =
        Regex("<(?:\\w+:)?$name>\\s*([^<]*?)\\s*</(?:\\w+:)?$name>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&apos;", "'")?.replace("&quot;", "\"")
            ?.takeIf { it.isNotBlank() }

    fun parseDescription(base: SsdpDevice, xml: String): SsdpDevice = base.copy(
        friendlyName = tag(xml, "friendlyName"),
        manufacturer = tag(xml, "manufacturer"),
        modelName = tag(xml, "modelName"),
        modelDescription = tag(xml, "modelDescription"),
        deviceType = tag(xml, "deviceType"),
        udn = tag(xml, "UDN"),
        macAddress = tag(xml, "macAddress"),
    )

    private val UUID = Regex(
        "uuid:([0-9a-f]{8})-([0-9a-f]{4})-([0-9a-f])([0-9a-f]{3})-([0-9a-f]{4})-([0-9a-f]{12})",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Molti dispositivi mettono il proprio MAC negli ultimi 12 caratteri dell'UDN.
     * - UUID versione 1: per specifica quel campo è il MAC → accettato (SSDP).
     * - UUID versione 4: casuale → scartato.
     * - Altre versioni: candidato debole (SSDP_UDN), da confermare con il database dei produttori.
     */
    fun macFromUdn(udn: String): Pair<String, MacSource>? {
        val m = UUID.find(udn) ?: return null
        val version = m.groupValues[3]
        val node = m.groupValues[6]
        if (node.toSet().size <= 2) return null // es. 000000000000, 111111111111
        val mac = MacAddress.normalize(node) ?: return null
        return when (version) {
            "1" -> mac to MacSource.SSDP
            "4" -> null
            else -> mac to MacSource.SSDP_UDN
        }
    }
}

/** Discovery SSDP: M-SEARCH multicast, poi lettura dell'XML di descrizione di ogni dispositivo. */
class SsdpDiscovery(private val listenMs: Long = 3000, private val httpTimeoutMs: Int = 2000) {

    suspend fun discover(selfIp: String, diag: ScanDiagnostics? = null, accept: (String) -> Boolean): Map<String, SsdpDevice> =
        coroutineScope {
            val all = withContext(Dispatchers.IO) { search(selfIp, diag) }
            diag?.log(TAG, "Risposte da ${all.size} host" + if (all.isEmpty()) " (nessun dispositivo UPnP ha risposto)" else "")
            val found = all.filterKeys(accept)
            found.values.map { d ->
                async(Dispatchers.IO) {
                    val xml = d.location?.let { fetch(it, d.ip, diag) }
                    val full = if (xml != null) SsdpParser.parseDescription(d, xml) else d
                    diag?.log(TAG, "${d.ip}: server=${d.server ?: "-"} location=${d.location ?: "-"} xml=${if (xml != null) "ok" else "no"} " +
                        "nome=${full.friendlyName ?: "-"} marca=${full.manufacturer ?: "-"} modello=${full.modelName ?: "-"} " +
                        "udn=${full.udn ?: "-"} mac=${full.macCandidates().joinToString { "${it.mac}(${it.source})" }.ifEmpty { "-" }}")
                    full
                }
            }.awaitAll().associateBy { it.ip }
        }

    private fun search(selfIp: String, diag: ScanDiagnostics?): Map<String, SsdpDevice> {
        val byIp = HashMap<String, SsdpDevice>()
        val rootSeen = HashSet<String>()
        try {
            MulticastSocket(0).use { s ->
                runCatching {
                    NetworkInterface.getByInetAddress(InetAddress.getByName(selfIp))?.let { s.networkInterface = it }
                }
                s.timeToLive = 4
                s.soTimeout = 200
                val group = InetAddress.getByName("239.255.255.250")
                for (st in listOf("upnp:rootdevice", "ssdp:all")) {
                    val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\nST: $st\r\nUSER-AGENT: Android UPnP/1.1 OpenLAN/1.0\r\n\r\n"
                    val b = msg.toByteArray(Charsets.US_ASCII)
                    repeat(2) { runCatching { s.send(DatagramPacket(b, b.size, group, 1900)) } }
                }
                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + listenMs
                while (System.currentTimeMillis() < deadline) {
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        s.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val ip = p.address?.hostAddress ?: continue
                    val h = SsdpParser.parseHeaders(String(p.data, 0, p.length, Charsets.UTF_8))
                    val existing = byIp[ip]
                    val isRoot = h["ST"]?.contains("rootdevice", ignoreCase = true) == true
                    // Si preferisce la LOCATION del dispositivo radice (la prima risposta può essere di un sottoservizio)
                    if (existing == null || (isRoot && ip !in rootSeen)) {
                        byIp[ip] = SsdpDevice(ip, h["LOCATION"] ?: existing?.location, h["SERVER"] ?: existing?.server)
                        if (isRoot) rootSeen += ip
                    }
                }
            }
        } catch (e: Exception) {
            diag?.log(TAG, "Errore: ${e.javaClass.simpleName}: ${e.message}")
        }
        return byIp
    }

    /**
     * Scarica l'XML di descrizione sempre dall'IP che ha risposto (niente richieste verso terzi).
     * Se LOCATION indica un altro indirizzo (es. un NAS con più schede di rete) si usa lo stesso porta/percorso sull'IP che ha risposto.
     */
    private fun fetch(location: String, ip: String, diag: ScanDiagnostics?): String? = try {
        val original = URL(location)
        val url = if (original.host == ip) original else URL(original.protocol, ip, original.port, original.file)
        if (url.protocol != "http") {
            null
        } else {
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = httpTimeoutMs
                readTimeout = httpTimeoutMs
                instanceFollowRedirects = false
                try {
                    if (responseCode == 200) inputStream.use { it.readBytes().decodeToString().take(200_000) }
                    else null.also { diag?.log(TAG, "$ip: descrizione XML HTTP $responseCode") }
                } finally {
                    disconnect()
                }
            }
        }
    } catch (e: Exception) {
        diag?.log(TAG, "$ip: descrizione XML non scaricata (${e.javaClass.simpleName}: ${e.message})")
        null
    }

    private companion object {
        const val TAG = "SSDP / UPnP"
    }
}
