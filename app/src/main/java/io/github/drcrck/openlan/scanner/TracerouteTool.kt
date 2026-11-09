package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

enum class HopZone(val label: String) {
    LAN("Rete locale"),
    CGNAT("CGNAT operatore"),
    INTERNET("Internet"),
}

data class Hop(
    val ttl: Int,
    val ip: String?,
    /** Tempo misurato dall'app (include l'avvio del processo ping: valore indicativo). */
    val rttMs: Long?,
    val reached: Boolean,
    val hostname: String? = null,
) {
    val zone: HopZone? get() = ip?.let { TracerouteTool.zoneOf(it) }
}

object TracerouteParser {
    private val FROM = Regex("(?i)from\\s+(?:[^\\s(]+\\s+\\()?(\\d{1,3}(?:\\.\\d{1,3}){3})")
    private val TIME = Regex("time[=<]([0-9.]+)\\s*ms")

    /** Interpreta l'output di `ping -c 1 -t TTL`: IP che ha risposto, destinazione raggiunta, tempo. */
    fun parse(output: String): Triple<String?, Boolean, Double?> {
        val reached = output.lineSequence().any { it.contains("bytes from", ignoreCase = true) }
        val ip = FROM.find(output)?.groupValues?.get(1)
        val time = if (reached) TIME.find(output)?.groupValues?.get(1)?.toDoubleOrNull() else null
        return Triple(ip, reached, time)
    }
}

/**
 * Traceroute con il binario `ping` di sistema e TTL crescente (il binario `traceroute` di solito non c'è).
 * Tutti i TTL partono in parallelo: il risultato arriva in pochi secondi.
 */
class TracerouteTool(private val reverseDns: ReverseDns) {

    suspend fun trace(target: String, maxHops: Int = 30, onUpdate: (List<Hop>) -> Unit): List<Hop> = coroutineScope {
        val ip = withContext(Dispatchers.IO) {
            InetAddress.getAllByName(target).firstOrNull { it is Inet4Address }?.hostAddress
        } ?: throw IllegalArgumentException("Impossibile risolvere $target")

        val results = java.util.concurrent.ConcurrentHashMap<Int, Hop>()
        fun publish() = onUpdate(trim(results.values.sortedBy { it.ttl }))

        (1..maxHops).map { ttl ->
            async(Dispatchers.IO) {
                results[ttl] = probe(ip, ttl)
                publish()
            }
        }.awaitAll()

        val hops = trim(results.values.sortedBy { it.ttl })
        val named = hops.map { h ->
            async { if (h.ip != null) h.copy(hostname = reverseDns.lookup(h.ip)) else h }
        }.awaitAll()
        onUpdate(named)
        named
    }

    /** Taglia dopo il primo hop che ha raggiunto la destinazione. */
    private fun trim(hops: List<Hop>): List<Hop> {
        val end = hops.indexOfFirst { it.reached }
        return if (end >= 0) hops.subList(0, end + 1) else hops
    }

    private fun probe(ip: String, ttl: Int): Hop = try {
        val start = System.nanoTime()
        val p = ProcessBuilder("ping", "-c", "1", "-W", "2", "-t", ttl.toString(), ip)
            .redirectErrorStream(true).start()
        val out = p.inputStream.use { it.readBytes().decodeToString() }
        p.waitFor(4, TimeUnit.SECONDS)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        val (from, reached, time) = TracerouteParser.parse(out)
        Hop(ttl, from, if (from != null) time?.toLong() ?: elapsed else null, reached)
    } catch (_: Exception) {
        Hop(ttl, null, null, false)
    }

    companion object {
        fun zoneOf(ip: String): HopZone {
            val n = Ipv4.toInt(ip) ?: return HopZone.INTERNET
            fun inNet(net: String, prefix: Int) = (n and Ipv4.mask(prefix)) == (Ipv4.toInt(net)!! and Ipv4.mask(prefix))
            return when {
                inNet("10.0.0.0", 8) || inNet("172.16.0.0", 12) || inNet("192.168.0.0", 16) || inNet("169.254.0.0", 16) -> HopZone.LAN
                inNet("100.64.0.0", 10) -> HopZone.CGNAT
                else -> HopZone.INTERNET
            }
        }
    }
}
