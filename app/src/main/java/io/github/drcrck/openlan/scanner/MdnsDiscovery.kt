package io.github.drcrck.openlan.scanner

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Quanto mDNS / Bonjour ci dice di un host. */
data class MdnsHostInfo(
    val ip: String,
    /** Nome .local senza suffisso (es. "MacBook-Pro"). */
    val hostname: String?,
    /** Tipi di servizio (es. "_airplay._tcp"). */
    val services: Set<String>,
    /** Tipo di servizio → nome istanza (es. "_airplay._tcp" → "TV Soggiorno"). */
    val instanceNames: Map<String, String>,
    /** Tutte le coppie TXT dell'host, chiavi minuscole. */
    val txt: Map<String, String>,
) {
    private fun instance(prefix: String) = instanceNames.entries.firstOrNull { it.key.startsWith(prefix) }?.value

    /** Nome "umano" pubblicato dal dispositivo. */
    val friendlyName: String?
        get() = txt["fn"]?.takeIf { it.isNotBlank() }
            ?: instance("_airplay.")
            ?: instance("_raop.")?.substringAfter('@')
            ?: instance("_ipp.") ?: instance("_ipps.") ?: instance("_printer.") ?: instance("_pdl-datastream.")
            ?: instance("_companion-link.")
            ?: instance("_smb.")
            ?: instance("_workstation.")?.substringBefore(" [")
            ?: instance("_spotify-connect.")

    val model: String?
        get() = listOf("md", "model", "rpmd", "ty", "am", "product", "usb_mdl")
            .firstNotNullOfOrNull { k -> txt[k]?.trim()?.removeSurrounding("(", ")")?.takeIf { it.isNotBlank() } }

    val manufacturer: String?
        get() = listOf("usb_mfg", "manufacturer", "mfg").firstNotNullOfOrNull { txt[it]?.takeIf { v -> v.isNotBlank() } }

    /** MAC pubblicati in nomi istanza o record TXT. */
    fun macCandidates(): List<MacCandidate> = buildList {
        instanceNames.forEach { (type, name) ->
            if (type.startsWith("_raop.")) RAOP_MAC.find(name)?.let { add(MacCandidate(it.groupValues[1], MacSource.MDNS_NAME)) }
            WORKSTATION_MAC.find(name)?.let { add(MacCandidate(it.groupValues[1], MacSource.MDNS_NAME)) }
        }
        for (key in listOf("deviceid", "mac", "macaddress", "mac_address", "hwaddr")) {
            txt[key]?.let { add(MacCandidate(it, MacSource.MDNS_TXT)) }
        }
    }

    companion object {
        private val RAOP_MAC = Regex("^([0-9A-Fa-f]{12})@")
        private val WORKSTATION_MAC = Regex("\\[([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})]")
    }
}

/** Mette insieme i record ricevuti da più pacchetti e li attribuisce agli IP giusti. */
class MdnsAggregator {
    private val learnedTypes = HashSet<String>()
    private val instances = LinkedHashSet<String>()
    private val instanceSource = HashMap<String, String>()
    private val instanceTarget = HashMap<String, String>()
    private val instanceTxt = HashMap<String, Map<String, String>>()
    private val hostIp = HashMap<String, String>()
    private val hostOriginal = HashMap<String, String>()
    private val reverse = HashMap<String, String>()
    private val responders = HashSet<String>()

    @Synchronized
    fun add(sourceIp: String, records: List<DnsRecord>) {
        if (records.isNotEmpty()) responders += sourceIp
        for (r in records) when (r) {
            is DnsRecord.Ptr -> {
                val revIp = DnsCodec.ipFromReverseName(r.name)
                when {
                    r.name.equals(SERVICES, ignoreCase = true) -> learnedTypes += r.target.lowercase().trimEnd('.')
                    revIp != null -> reverse[revIp] = r.target
                    splitInstance(r.target) != null -> {
                        instances += r.target
                        instanceSource.putIfAbsent(r.target, sourceIp)
                    }
                }
            }
            is DnsRecord.Srv -> if (splitInstance(r.name) != null) {
                instances += r.name
                instanceSource.putIfAbsent(r.name, sourceIp)
                instanceTarget[r.name] = r.target
            }
            is DnsRecord.Txt -> if (splitInstance(r.name) != null) {
                instances += r.name
                instanceSource.putIfAbsent(r.name, sourceIp)
                instanceTxt[r.name] = (instanceTxt[r.name] ?: emptyMap()) + r.entries
            }
            is DnsRecord.A -> {
                hostIp[r.name.lowercase()] = r.ip
                hostOriginal[r.name.lowercase()] = r.name
            }
        }
    }

    @Synchronized fun learnedTypes(): Set<String> = learnedTypes.toSet()
    @Synchronized fun responders(): Set<String> = responders.toSet()

    @Synchronized
    fun result(): Map<String, MdnsHostInfo> {
        val hostnames = HashMap<String, String>()
        val services = HashMap<String, MutableSet<String>>()
        val names = HashMap<String, MutableMap<String, String>>()
        val txt = HashMap<String, MutableMap<String, String>>()

        hostIp.forEach { (host, ip) -> hostnames.putIfAbsent(ip, hostOriginal[host] ?: host) }
        reverse.forEach { (ip, host) -> hostnames[ip] = host }

        for (inst in instances) {
            val (instanceName, type) = splitInstance(inst) ?: continue
            val target = instanceTarget[inst]
            // L'IP viene dal record A del target (può rispondere un proxy, es. Sleep Proxy), altrimenti dal mittente
            val ip = target?.let { hostIp[it.lowercase()] } ?: instanceSource[inst] ?: continue
            target?.let { hostnames.putIfAbsent(ip, it) }
            services.getOrPut(ip) { sortedSetOf() } += type
            names.getOrPut(ip) { LinkedHashMap() }.putIfAbsent(type, instanceName)
            instanceTxt[inst]?.let { t -> txt.getOrPut(ip) { LinkedHashMap() }.putAll(t) }
        }

        val ips = hostnames.keys + services.keys
        return ips.associateWith { ip ->
            MdnsHostInfo(
                ip = ip,
                hostname = hostnames[ip]?.trimEnd('.')?.removeSuffix(".local")?.takeIf { it.isNotBlank() },
                services = services[ip] ?: emptySet(),
                instanceNames = names[ip] ?: emptyMap(),
                txt = txt[ip] ?: emptyMap(),
            )
        }
    }

    companion object {
        const val SERVICES = "_services._dns-sd._udp.local"
        private val INSTANCE = Regex("^(.+?)\\.(_[^.]+\\._(?:tcp|udp))\\.local\\.?$", RegexOption.IGNORE_CASE)

        /** "TV Soggiorno._airplay._tcp.local" → ("TV Soggiorno", "_airplay._tcp") */
        fun splitInstance(fullName: String): Pair<String, String>? {
            if (fullName.contains("._sub.")) return null
            val m = INSTANCE.find(fullName) ?: return null
            return m.groupValues[1] to m.groupValues[2].lowercase()
        }
    }
}


/**
 * Discovery mDNS senza librerie, su due socket:
 * - porta casuale: query "legacy unicast", i dispositivi rispondono direttamente a noi;
 * - porta 5353 iscritta al gruppo 224.0.0.251 (con MulticastLock): riceve le risposte inviate in multicast.
 * Si interroga sia il gruppo multicast sia ogni host noto.
 */
class MdnsDiscovery(context: Context, private val roundMs: Long = 2500) {

    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    suspend fun discover(selfIp: String, hosts: Collection<String>, diag: ScanDiagnostics? = null): Map<String, MdnsHostInfo> =
        withContext(Dispatchers.IO) {
            val agg = MdnsAggregator()
            val packets = ConcurrentHashMap<String, AtomicInteger>()
            val lock = runCatching {
                wifi?.createMulticastLock("netscan-mdns")?.apply { setReferenceCounted(false); acquire() }
            }.onFailure { diag?.log(TAG, "MulticastLock non ottenuto: ${it.message}") }.getOrNull()

            try {
                val nif = runCatching { NetworkInterface.getByInetAddress(InetAddress.getByName(selfIp)) }.getOrNull()
                diag?.log(TAG, "Interfaccia: ${nif?.name ?: "non trovata"} · MulticastLock: ${if (lock?.isHeld == true) "sì" else "no"}")
                val group = InetAddress.getByName(GROUP)

                val uni = MulticastSocket(0).apply {
                    nif?.let { runCatching { networkInterface = it } }
                    timeToLive = 255
                    soTimeout = 200
                }
                val multi = openListener(group, nif, diag)
                try {
                    val common = COMMON_TYPES.map { "$it.local" }
                    val query = DnsCodec.buildQuery(0, (listOf(MdnsAggregator.SERVICES) + common).map { it to DnsCodec.TYPE_PTR })
                    send(uni, query, group, diag)
                    multi?.let { send(it, query, group, diag) } // da 5353: risposte in multicast
                    for (h in hosts) {
                        val addr = InetAddress.getByName(h)
                        send(uni, query, addr, null)
                        send(uni, DnsCodec.buildQuery(0x4E54, listOf(DnsCodec.reverseName(h) to DnsCodec.TYPE_PTR)), addr, null)
                    }
                    receiveBoth(uni, multi, agg, packets, roundMs)

                    // Secondo giro: tipi di servizio scoperti tramite _services._dns-sd
                    val extra = (agg.learnedTypes() - common.toSet()).take(30)
                    if (extra.isNotEmpty()) {
                        diag?.log(TAG, "Altri tipi di servizio scoperti: ${extra.joinToString()}")
                        val q2 = DnsCodec.buildQuery(0, extra.map { it to DnsCodec.TYPE_PTR })
                        send(uni, q2, group, diag)
                        multi?.let { send(it, q2, group, diag) }
                        agg.responders().forEach { send(uni, q2, InetAddress.getByName(it), null) }
                        receiveBoth(uni, multi, agg, packets, roundMs * 3 / 4)
                    }
                } finally {
                    uni.close()
                    multi?.close()
                }
            } catch (e: Exception) {
                diag?.log(TAG, "Errore: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                runCatching { lock?.release() }
            }

            val result = agg.result()
            diag?.log(TAG, "Pacchetti ricevuti per mittente: " +
                (packets.entries.sortedBy { Ipv4.sortKey(it.key) }.joinToString { "${it.key}×${it.value}" }.ifEmpty { "nessuno" }))
            result.values.sortedBy { Ipv4.sortKey(it.ip) }.forEach { h ->
                diag?.log(TAG, "${h.ip}: host=${h.hostname ?: "-"} servizi=${h.services.joinToString(",").ifEmpty { "-" }} " +
                    "nome=${h.friendlyName ?: "-"} modello=${h.model ?: "-"} mac=${h.macCandidates().joinToString { it.mac }.ifEmpty { "-" }}")
            }
            result
        }

    private fun openListener(group: InetAddress, nif: NetworkInterface?, diag: ScanDiagnostics?): MulticastSocket? = try {
        MulticastSocket(null as SocketAddress?).apply {
            reuseAddress = true
            bind(InetSocketAddress(PORT))
            if (nif != null) {
                networkInterface = nif
                joinGroup(InetSocketAddress(group, PORT), nif)
            } else {
                @Suppress("DEPRECATION")
                joinGroup(group)
            }
            timeToLive = 255
            soTimeout = 200
        }
    } catch (e: Exception) {
        diag?.log(TAG, "Ascolto multicast su 5353 non disponibile: ${e.message}")
        null
    }

    private fun send(s: MulticastSocket, data: ByteArray, to: InetAddress, diag: ScanDiagnostics?) {
        runCatching { s.send(DatagramPacket(data, data.size, to, PORT)) }
            .onFailure { diag?.log(TAG, "Invio a ${to.hostAddress} fallito: ${it.message}") }
    }

    private suspend fun receiveBoth(
        uni: MulticastSocket,
        multi: MulticastSocket?,
        agg: MdnsAggregator,
        packets: ConcurrentHashMap<String, AtomicInteger>,
        durationMs: Long,
    ) = coroutineScope {
        launch(Dispatchers.IO) { receive(uni, agg, packets, durationMs) }
        if (multi != null) launch(Dispatchers.IO) { receive(multi, agg, packets, durationMs) }
    }

    private fun receive(
        s: MulticastSocket,
        agg: MdnsAggregator,
        packets: ConcurrentHashMap<String, AtomicInteger>,
        durationMs: Long,
    ) {
        val buf = ByteArray(9000)
        val deadline = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < deadline) {
            val p = DatagramPacket(buf, buf.size)
            try {
                s.receive(p)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: Exception) {
                return
            }
            val ip = p.address?.hostAddress ?: continue
            val records = DnsCodec.parseRecords(p.data, p.length)
            // Solo risposte (bit QR): le query di altri dispositivi non interessano
            if (p.length > 2 && (p.data[2].toInt() and 0x80) != 0) {
                packets.getOrPut(ip) { AtomicInteger() }.incrementAndGet()
                agg.add(ip, records)
            }
        }
    }

    companion object {
        private const val TAG = "mDNS"
        private const val GROUP = "224.0.0.251"
        private const val PORT = 5353

        val COMMON_TYPES = listOf(
            "_airplay._tcp", "_raop._tcp", "_googlecast._tcp", "_spotify-connect._tcp", "_sonos._tcp",
            "_amzn-wplay._tcp", "_androidtvremote2._tcp",
            "_ipp._tcp", "_ipps._tcp", "_printer._tcp", "_pdl-datastream._tcp", "_uscan._tcp", "_scanner._tcp",
            "_smb._tcp", "_afpovertcp._tcp", "_workstation._tcp", "_device-info._tcp", "_companion-link._tcp",
            "_apple-mobdev2._tcp", "_ssh._tcp", "_sftp-ssh._tcp", "_rfb._tcp", "_http._tcp",
            "_hap._tcp", "_homekit._tcp", "_hue._tcp", "_esphomelib._tcp", "_shelly._tcp", "_matter._tcp",
            "_sleep-proxy._udp",
        )
    }
}
