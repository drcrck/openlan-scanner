package io.github.drcrck.openlan.scanner

import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.data.oui.OuiLookup
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

enum class ScanPhase { DISCOVERY, SERVICES, NAMES }

data class ScanProgress(val phase: ScanPhase, val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Host trovato durante una scansione. */
data class DiscoveredHost(
    val ip: String,
    val isSelf: Boolean,
    val isGateway: Boolean,
    val mac: String?,
    val macSource: MacSource?,
    val vendor: String?,
    val netbiosName: String?,
    val hostname: String?,
    val mdnsName: String? = null,
    val friendlyName: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    val ssdpUdn: String? = null,
    val services: List<String> = emptyList(),
    val deviceType: DeviceType = DeviceType.UNKNOWN,
)

data class ScanOutcome(val hosts: List<DiscoveredHost>, val range: ScanRange, val diagnostics: ScanDiagnostics)

/**
 * Scansione della subnet:
 * 1. ping/TCP su ogni IP + NetBIOS in parallelo;
 * 2. mDNS e SSDP (nomi, servizi, MAC);
 * 3. DNS inverso, produttore e tipo di dispositivo.
 */
class ScanEngine(
    private val prober: HostProber,
    private val netbios: NetbiosDiscovery,
    private val mdns: MdnsDiscovery,
    private val ssdp: SsdpDiscovery,
    private val reverseDns: ReverseDns,
    private val oui: OuiLookup,
    private val probeConcurrency: Int = 64,
) {

    suspend fun scan(info: NetworkInfo, onProgress: (ScanProgress) -> Unit): ScanOutcome = coroutineScope {
        val diag = ScanDiagnostics()
        val range = Ipv4.hostsToScan(info.ipAddress, info.prefixLength)
        val inRange = range.hosts.toHashSet()
        diag.log("Rete", "Telefono ${info.ipAddress}/${info.prefixLength} · gateway ${info.gateway ?: "-"} · " +
            "DNS ${info.dnsServers.joinToString().ifEmpty { "-" }} · DHCP ${info.dhcpServer ?: "-"} · BSSID ${info.bssid ?: "non leggibile"}")
        diag.log("Rete", "Indirizzi da sondare: ${range.hosts.size} (/${range.effectivePrefix}${if (range.limited) ", ridotto" else ""})")
        val total = range.hosts.size
        val done = AtomicInteger(0)
        onProgress(ScanProgress(ScanPhase.DISCOVERY, 0, total))

        // 1. NetBIOS su tutta la subnet mentre gira il sondaggio ping/TCP
        val netbiosJob = async { netbios.query(range.hosts, diag) }
        val sem = Semaphore(probeConcurrency)
        val alive = range.hosts.map { ip ->
            async {
                val ok = sem.withPermit { prober.isAlive(ip) }
                onProgress(ScanProgress(ScanPhase.DISCOVERY, done.incrementAndGet(), total))
                ip.takeIf { ok }
            }
        }.awaitAll().filterNotNull().toMutableSet()
        diag.log("Ping / TCP", "Host attivi: ${alive.size}")
        val nb = netbiosJob.await()
        alive += nb.keys
        alive += info.ipAddress
        info.gateway?.takeIf { it in inRange }?.let { alive += it }

        // 2. mDNS e SSDP: possono trovare anche host che non rispondono a ping/TCP
        onProgress(ScanProgress(ScanPhase.SERVICES, 0, 1))
        val others = alive.filter { it != info.ipAddress }
        val mdnsJob = async { mdns.discover(info.ipAddress, others, diag) }
        val ssdpJob = async { ssdp.discover(info.ipAddress, diag) { it in inRange } }
        val md = mdnsJob.await().filterKeys { it in inRange }
        val up = ssdpJob.await()
        alive += md.keys
        alive += up.keys

        // 3. Nomi DNS e assemblaggio
        val names = alive.toList()
        val namesDone = AtomicInteger(0)
        onProgress(ScanProgress(ScanPhase.NAMES, 0, names.size))
        val dnsSem = Semaphore(32)
        val dnsServers = (info.dnsServers + listOfNotNull(info.gateway)).distinct()
        val rdnsOk = AtomicInteger(0)
        val hosts = names.map { ip ->
            async {
                val hostname = dnsSem.withPermit { reverseDns.lookup(ip, dnsServers) }
                if (hostname != null) rdnsOk.incrementAndGet()
                onProgress(ScanProgress(ScanPhase.NAMES, namesDone.incrementAndGet(), names.size))
                buildHost(ip, info, nb[ip], md[ip], up[ip], hostname)
            }
        }.awaitAll()

        diag.log("DNS inverso", "Server interrogati: ${dnsServers.joinToString().ifEmpty { "nessuno (solo resolver di sistema)" }} · " +
            "nomi trovati: ${rdnsOk.get()} su ${names.size}")
        hosts.filter { it.hostname != null }.sortedBy { Ipv4.sortKey(it.ip) }
            .forEach { diag.log("DNS inverso", "${it.ip}: ${it.hostname}") }
        val sorted = hosts.sortedBy { Ipv4.sortKey(it.ip) }
        diag.log("Riepilogo", "Dispositivi: ${sorted.size} · con MAC: ${sorted.count { it.mac != null }}")
        sorted.forEach { h ->
            diag.log("Riepilogo", "${h.ip}: mac=${h.mac?.let { "$it (${h.macSource?.label})" } ?: "-"} " +
                "nome=${h.friendlyName ?: h.netbiosName ?: h.mdnsName ?: "-"} dns=${h.hostname ?: "-"} " +
            "produttore=${h.vendor ?: "-"} tipo=${h.deviceType.label}")
        }
        ScanOutcome(sorted, range, diag)
    }

    private suspend fun buildHost(
        ip: String,
        info: NetworkInfo,
        nb: NetbiosInfo?,
        md: MdnsHostInfo?,
        up: SsdpDevice?,
        hostname: String?,
    ): DiscoveredHost {
        val isSelf = ip == info.ipAddress
        val isGateway = ip == info.gateway

        val candidates = buildList {
            nb?.mac?.let { add(MacCandidate(it, MacSource.NETBIOS)) }
            md?.let { addAll(it.macCandidates()) }
            up?.let { u ->
                u.macCandidates().forEach { c ->
                    // MAC dedotto dall'UDN: solo se il prefisso è di un produttore reale
                    if (c.source != MacSource.SSDP_UDN || oui.vendorFor(c.mac) != null) add(c)
                }
            }
            // Il BSSID non si attribuisce al gateway: è il MAC dell'access point Wi-Fi,
            // che in molte reti (uffici, mesh, AP separati) è un apparato diverso dal router.
        }
        // Il MAC del telefono stesso è randomizzato e Android non lo espone: non lo cerchiamo
        val mac = if (isSelf) null else MacResolver.resolve(candidates)
        val vendor = oui.vendorFor(mac?.mac)

        val friendly = up?.friendlyName ?: md?.friendlyName
        val model = md?.model ?: up?.modelName
        val manufacturer = up?.manufacturer ?: md?.manufacturer
        val services = md?.services?.sorted().orEmpty()

        val type = DeviceClassifier.classify(
            ClassifierInput(
                isGateway = isGateway,
                isSelf = isSelf,
                vendor = vendor,
                services = services.toSet(),
                model = listOfNotNull(model, up?.modelDescription).joinToString(" "),
                manufacturer = manufacturer,
                upnpDeviceType = up?.deviceType,
                upnpServer = up?.server,
                netbiosName = nb?.name,
                names = listOfNotNull(friendly, md?.hostname, hostname, nb?.name),
            )
        )

        return DiscoveredHost(
            ip = ip,
            isSelf = isSelf,
            isGateway = isGateway,
            mac = mac?.mac,
            macSource = mac?.source,
            vendor = vendor,
            netbiosName = nb?.name,
            hostname = hostname,
            mdnsName = md?.hostname,
            friendlyName = friendly,
            model = model,
            manufacturer = manufacturer,
            ssdpUdn = up?.udn,
            services = services,
            deviceType = type,
        )
    }
}
