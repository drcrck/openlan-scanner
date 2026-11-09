package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object PingParser {
    private val TIME = Regex("time[=<]\\s*([0-9.]+)\\s*ms")

    /** Tempo di risposta in ms dall'output di `ping -c 1`; null se nessuna risposta. */
    fun parseTime(output: String): Double? {
        if (!output.contains("bytes from", ignoreCase = true)) return null
        return TIME.find(output)?.groupValues?.get(1)?.toDoubleOrNull()
    }
}

/** Un singolo ping con il binario di sistema; restituisce il tempo in ms o null se perso. */
class PingTool {
    suspend fun ping(ip: String, timeoutSec: Int = 1): Double? = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("ping", "-c", "1", "-W", timeoutSec.toString(), ip)
                .redirectErrorStream(true).start()
            val out = p.inputStream.use { it.readBytes().decodeToString() }
            p.waitFor(timeoutSec + 2L, TimeUnit.SECONDS)
            PingParser.parseTime(out)
        } catch (_: Exception) {
            null
        }
    }
}

/** Serie di ping con statistiche; [samples] tiene gli ultimi [WINDOW] valori (null = perso). */
data class PingStats(
    val samples: List<Double?> = emptyList(),
    val sent: Int = 0,
    val received: Int = 0,
    val min: Double? = null,
    val max: Double? = null,
    val sum: Double = 0.0,
) {
    val last: Double? get() = samples.lastOrNull()
    val avg: Double? get() = if (received == 0) null else sum / received
    val lossPercent: Double get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent

    fun add(rtt: Double?): PingStats = copy(
        samples = (samples + rtt).takeLast(WINDOW),
        sent = sent + 1,
        received = received + if (rtt != null) 1 else 0,
        min = if (rtt == null) min else minOf(min ?: rtt, rtt),
        max = if (rtt == null) max else maxOf(max ?: rtt, rtt),
        sum = sum + (rtt ?: 0.0),
    )

    companion object {
        const val WINDOW = 60
    }
}
