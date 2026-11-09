package io.github.drcrck.openlan.scanner

/** Origine di un MAC, in ordine di affidabilità (priorità più alta = più affidabile). */
enum class MacSource(val priority: Int, val label: String) {
    NETBIOS(5, "NetBIOS"),
    MDNS_TXT(5, "mDNS"),
    MDNS_NAME(4, "mDNS"),
    SSDP(3, "UPnP"),
    /** Dedotto dall'UDN UPnP: accettato solo se il prefisso corrisponde a un produttore noto. */
    SSDP_UDN(2, "UPnP (UDN)"),
    BSSID(1, "BSSID Wi-Fi"),
}

data class MacCandidate(val mac: String, val source: MacSource)

/** Sceglie il MAC più affidabile tra quelli raccolti dai vari protocolli. */
object MacResolver {

    fun resolve(candidates: List<MacCandidate>): MacCandidate? =
        candidates
            .mapNotNull { c -> MacAddress.normalize(c.mac)?.let { c.copy(mac = it) } }
            .maxByOrNull { it.source.priority }
}
