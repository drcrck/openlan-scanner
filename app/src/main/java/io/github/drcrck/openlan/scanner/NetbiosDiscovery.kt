package io.github.drcrck.openlan.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Risposta NetBIOS NBSTAT di un host. */
data class NetbiosInfo(
    val ip: String,
    val name: String?,
    val workgroup: String?,
    val mac: String?,
)

/** Codifica/decodifica dei pacchetti NBSTAT (RFC 1002, sez. 4.2.17-4.2.18). */
object NetbiosCodec {

    /** Query NBSTAT per il nome jolly "*": 50 byte. */
    fun buildNbstatQuery(transactionId: Int): ByteArray {
        val out = ByteArray(50)
        out[0] = (transactionId ushr 8).toByte()
        out[1] = transactionId.toByte()
        // flag = 0, QDCOUNT = 1, AN/NS/AR = 0
        out[5] = 1
        // Nome: lunghezza 32 + "*" seguito da 15 byte 0x00, in "first-level encoding"
        out[12] = 0x20
        val raw = ByteArray(16).also { it[0] = '*'.code.toByte() }
        for (i in 0 until 16) {
            val b = raw[i].toInt() and 0xFF
            out[13 + i * 2] = ('A'.code + (b ushr 4)).toByte()
            out[14 + i * 2] = ('A'.code + (b and 0x0F)).toByte()
        }
        out[45] = 0 // fine nome
        out[46] = 0x00; out[47] = 0x21 // tipo NBSTAT
        out[48] = 0x00; out[49] = 0x01 // classe IN
        return out
    }

    data class Parsed(val transactionId: Int, val name: String?, val workgroup: String?, val mac: String?)

    fun parseNbstatResponse(data: ByteArray, length: Int = data.size): Parsed? {
        fun u8(i: Int) = data[i].toInt() and 0xFF
        fun u16(i: Int) = (u8(i) shl 8) or u8(i + 1)
        try {
            if (length < 12 + 2 + 10 + 1) return null
            if (u8(2) and 0x80 == 0) return null // non è una risposta
            if (u16(6) < 1) return null // nessuna answer
            var off = 12
            if (u8(off) and 0xC0 == 0xC0) {
                off += 2 // nome compresso (puntatore)
            } else {
                while (off < length && u8(off) != 0) off += u8(off) + 1
                off += 1
            }
            off += 10 // tipo, classe, TTL, RDLENGTH
            if (off >= length) return null
            val count = u8(off)
            off += 1
            var name: String? = null
            var group: String? = null
            repeat(count) {
                if (off + 18 > length) return@repeat
                val n = String(data, off, 15, Charsets.US_ASCII).trim { it <= ' ' }
                val suffix = u8(off + 15)
                val isGroup = u16(off + 16) and 0x8000 != 0
                if (suffix == 0x00 || suffix == 0x20) {
                    if (!isGroup && name == null && n.isNotEmpty()) name = n
                    if (isGroup && suffix == 0x00 && group == null && n.isNotEmpty()) group = n
                }
                off += 18
            }
            val mac = if (off + 6 <= length) MacAddress.fromBytes(data, off) else null
            return Parsed(u16(0), name, group, mac)
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
    }
}

/**
 * Invia una query NBSTAT (UDP 137) a ogni IP e raccoglie le risposte.
 * Un solo socket per tutti gli host: veloce anche su un /22.
 */
class NetbiosDiscovery(private val timeoutMs: Long = 2500) {

    suspend fun query(ips: List<String>, diag: ScanDiagnostics? = null): Map<String, NetbiosInfo> = withContext(Dispatchers.IO) {
        val results = HashMap<String, NetbiosInfo>()
        if (ips.isEmpty()) return@withContext results
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 250
                ips.forEachIndexed { i, ip ->
                    val q = NetbiosCodec.buildNbstatQuery(i and 0xFFFF)
                    runCatching { socket.send(DatagramPacket(q, q.size, InetAddress.getByName(ip), 137)) }
                }
                val buf = ByteArray(1024)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val ip = packet.address?.hostAddress ?: continue
                    val parsed = NetbiosCodec.parseNbstatResponse(packet.data, packet.length) ?: continue
                    results[ip] = NetbiosInfo(ip, parsed.name, parsed.workgroup, parsed.mac)
                }
            }
        } catch (e: Exception) {
            diag?.log("NetBIOS", "Errore: ${e.javaClass.simpleName}: ${e.message}")
        }
        diag?.log("NetBIOS", "Query inviate: ${ips.size} · risposte: ${results.size}")
        results.values.sortedBy { Ipv4.sortKey(it.ip) }.forEach {
            diag?.log("NetBIOS", "${it.ip}: nome=${it.name ?: "-"} gruppo=${it.workgroup ?: "-"} mac=${it.mac ?: "scartato/assente"}")
        }
        results
    }
}
