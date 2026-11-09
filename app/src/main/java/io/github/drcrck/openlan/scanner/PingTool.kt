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

/** Statistiche calcolate sulla stessa finestra mostrata dal grafico (ultimi [WINDOW] campioni). */
data class PingStats(
    val samples: List<Double?> = emptyList(),
) {
    private val ok: List<Double> get() = samples.filterNotNull()
    val sent: Int get() = samples.size
    val received: Int get() = ok.size
    val min: Double? get() = ok.minOrNull()
    val max: Double? get() = ok.maxOrNull()
    val last: Double? get() = samples.lastOrNull()
    val avg: Double? get() = if (ok.isEmpty()) null else ok.average()
    val lossPercent: Double get() = if (sent == 0) 0.0 else (sent - received) * 100.0 / sent

    fun add(rtt: Double?): PingStats = copy(samples = (samples + rtt).takeLast(WINDOW))

    companion object {
        const val WINDOW = 60
    }
}
