package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Verifica se un host è attivo: prima `ping` di sistema, poi tentativi TCP su porte comuni.
 * Una connessione rifiutata (RST) conta come "vivo": l'host ha risposto.
 */
class HostProber(
    private val tcpPorts: List<Int> = DEFAULT_PORTS,
    private val connectTimeoutMs: Int = 400,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(256),
) {

    suspend fun isAlive(ip: String): Boolean = withContext(io) {
        ping(ip) || tcpProbe(ip)
    }

    private fun ping(ip: String): Boolean = try {
        val process = ProcessBuilder("ping", "-c", "1", "-W", "1", ip)
            .redirectErrorStream(true)
            .start()
        process.inputStream.use { it.readBytes() }
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        if (!finished) process.destroy()
        finished && process.exitValue() == 0
    } catch (_: Exception) {
        false
    }

    private suspend fun tcpProbe(ip: String): Boolean = coroutineScope {
        tcpPorts.map { port -> async(io) { tryConnect(ip, port) } }.awaitAll().any { it }
    }

    private fun tryConnect(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), connectTimeoutMs) }
        true
    } catch (e: ConnectException) {
        e.message?.contains("ECONNREFUSED") == true
    } catch (_: Exception) {
        false
    }

    companion object {
        /** HTTP, HTTPS, SSH, SMB, NetBIOS, HTTP alt, iPhone sync, AirPlay. */
        val DEFAULT_PORTS = listOf(80, 443, 22, 445, 139, 8080, 62078, 7000)
    }
}
