package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

/**
 * Nome host tramite query PTR.
 * Prima interroga direttamente i DNS della rete (es. il server di dominio o il router, che conoscono i nomi DHCP):
 * il resolver di Android può usare DNS privati/esterni che non conoscono gli indirizzi locali.
 * Poi, come ripiego, il resolver di sistema.
 */
class ReverseDns(private val timeoutMs: Int = 1500) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(32))

    suspend fun lookup(ip: String, servers: List<String> = emptyList()): String? {
        for (server in servers.distinct()) {
            val name = withContext(Dispatchers.IO) { queryServer(ip, server) }
            if (name != null) return name
        }
        return systemLookup(ip)
    }

    private fun queryServer(ip: String, server: String): String? = try {
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs
            val id = Random.nextInt(1, 0xFFFF)
            val reverse = DnsCodec.reverseName(ip)
            val q = DnsCodec.buildQuery(id, listOf(reverse to DnsCodec.TYPE_PTR), recursion = true)
            s.send(DatagramPacket(q, q.size, InetAddress.getByName(server), 53))
            val buf = ByteArray(1500)
            val p = DatagramPacket(buf, buf.size)
            s.receive(p)
            val respId = ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF)
            if (respId != id) null
            else DnsCodec.parseRecords(buf, p.length)
                .filterIsInstance<DnsRecord.Ptr>()
                .firstOrNull { it.name.equals(reverse, ignoreCase = true) }
                ?.target?.trimEnd('.')?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun systemLookup(ip: String): String? {
        val deferred = scope.async {
            runCatching { InetAddress.getByName(ip).canonicalHostName }.getOrNull()
        }
        val name = withTimeoutOrNull(timeoutMs.toLong() * 2) { deferred.await() } ?: return null
        return name.trimEnd('.').takeIf { it.isNotBlank() && it != ip }
    }
}
