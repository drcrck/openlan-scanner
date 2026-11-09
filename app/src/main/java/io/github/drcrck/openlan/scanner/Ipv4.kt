package io.github.drcrck.openlan.scanner

/** Intervallo di indirizzi da scansionare. */
data class ScanRange(
    val hosts: List<String>,
    /** Prefisso effettivamente usato (può essere più stretto di quello reale della rete). */
    val effectivePrefix: Int,
    /** true se la rete era più grande di /22 e si scansiona solo il /24 del telefono. */
    val limited: Boolean,
)

object Ipv4 {

    const val MIN_PREFIX = 22

    fun toInt(ip: String): Int? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        var result = 0
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            result = (result shl 8) or n
        }
        return result
    }

    fun toString(ip: Int): String =
        "${(ip ushr 24) and 255}.${(ip ushr 16) and 255}.${(ip ushr 8) and 255}.${ip and 255}"

    fun mask(prefix: Int): Int = if (prefix == 0) 0 else -1 shl (32 - prefix)

    /** Indirizzo di rete in forma "a.b.c.d/p". */
    fun subnetLabel(ip: String, prefix: Int): String {
        val n = toInt(ip) ?: return "$ip/$prefix"
        return "${toString(n and mask(prefix))}/$prefix"
    }

    /**
     * Host da sondare per la rete del telefono. Reti più grandi di /22 (o /31-/32)
     * vengono ridotte al /24 del telefono per non lanciare scansioni enormi.
     */
    fun hostsToScan(ip: String, prefix: Int): ScanRange {
        val n = toInt(ip) ?: return ScanRange(emptyList(), prefix, false)
        val limited = prefix < MIN_PREFIX || prefix > 30
        val p = if (limited) 24 else prefix
        val network = n and mask(p)
        val broadcast = network or mask(p).inv()
        val hosts = ((network + 1) until broadcast).map { toString(it) }
        return ScanRange(hosts, p, limited)
    }

    /** Ordinamento numerico degli IP (per la lista). */
    fun sortKey(ip: String): Long = (toInt(ip)?.toLong() ?: 0L) and 0xFFFFFFFFL
}
