package io.github.drcrck.openlan.scanner

import java.io.ByteArrayOutputStream

/** Record DNS decodificati (solo i tipi utili a mDNS). */
sealed class DnsRecord {
    abstract val name: String

    data class Ptr(override val name: String, val target: String) : DnsRecord()
    data class Srv(override val name: String, val target: String, val port: Int) : DnsRecord()
    data class Txt(override val name: String, val entries: Map<String, String>) : DnsRecord()
    data class A(override val name: String, val ip: String) : DnsRecord()
}

/** Codifica query e decodifica risposte DNS / mDNS (RFC 1035, RFC 6762). */
object DnsCodec {

    const val TYPE_A = 1
    const val TYPE_PTR = 12
    const val TYPE_TXT = 16
    const val TYPE_SRV = 33

    /** [recursion] = flag RD, da usare verso un DNS normale (non per mDNS). */
    fun buildQuery(id: Int, questions: List<Pair<String, Int>>, recursion: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        out.write16(id)
        out.write16(if (recursion) 0x0100 else 0) // flag: query standard
        out.write16(questions.size)
        out.write16(0); out.write16(0); out.write16(0)
        for ((name, type) in questions) {
            writeName(out, name)
            out.write16(type)
            out.write16(1) // classe IN
        }
        return out.toByteArray()
    }

    /** Nome PTR per la ricerca inversa: 192.168.1.10 → 10.1.168.192.in-addr.arpa */
    fun reverseName(ip: String): String = ip.split(".").reversed().joinToString(".") + ".in-addr.arpa"

    fun ipFromReverseName(name: String): String? {
        val n = name.lowercase()
        if (!n.endsWith(".in-addr.arpa")) return null
        val parts = n.removeSuffix(".in-addr.arpa").split(".")
        if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return null
        return parts.reversed().joinToString(".")
    }

    /** Tutti i record (answer, authority, additional) di una risposta; lista vuota se il pacchetto non è valido. */
    fun parseRecords(data: ByteArray, length: Int = data.size): List<DnsRecord> {
        val r = Reader(data, length)
        return try {
            r.pos = 4
            val qd = r.u16(); val an = r.u16(); val ns = r.u16(); val ar = r.u16()
            repeat(qd) { r.name(); r.pos += 4 }
            val records = ArrayList<DnsRecord>()
            repeat(an + ns + ar) {
                val name = r.name()
                val type = r.u16()
                r.pos += 2 + 4 // classe (con bit cache-flush) + TTL
                val rdLen = r.u16()
                val start = r.pos
                if (start + rdLen > length) return records
                when (type) {
                    TYPE_PTR -> records += DnsRecord.Ptr(name, r.name())
                    TYPE_SRV -> {
                        r.pos += 4 // priorità, peso
                        val port = r.u16()
                        records += DnsRecord.Srv(name, r.name(), port)
                    }
                    TYPE_TXT -> records += DnsRecord.Txt(name, parseTxt(data, start, rdLen))
                    TYPE_A -> if (rdLen == 4) {
                        records += DnsRecord.A(name, (0 until 4).joinToString(".") { (data[start + it].toInt() and 0xFF).toString() })
                    }
                }
                r.pos = start + rdLen
            }
            records
        } catch (_: IndexOutOfBoundsException) {
            emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }
    }

    private fun parseTxt(data: ByteArray, start: Int, len: Int): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        var p = start
        while (p < start + len) {
            val l = data[p].toInt() and 0xFF
            p++
            if (l == 0 || p + l > start + len) { p += l; continue }
            val s = String(data, p, l, Charsets.UTF_8)
            val eq = s.indexOf('=')
            if (eq > 0) map[s.substring(0, eq).lowercase()] = s.substring(eq + 1)
            else if (eq < 0) map[s.lowercase()] = ""
            p += l
        }
        return map
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        for (label in name.trimEnd('.').split(".")) {
            val b = label.toByteArray(Charsets.UTF_8)
            out.write(b.size)
            out.write(b)
        }
        out.write(0)
    }

    private fun ByteArrayOutputStream.write16(v: Int) {
        write((v ushr 8) and 0xFF)
        write(v and 0xFF)
    }

    private class Reader(val data: ByteArray, val length: Int) {
        var pos = 0

        fun u8(i: Int): Int {
            if (i >= length) throw IndexOutOfBoundsException()
            return data[i].toInt() and 0xFF
        }

        fun u16(): Int = ((u8(pos) shl 8) or u8(pos + 1)).also { pos += 2 }

        /** Legge un nome gestendo i puntatori di compressione; avanza [pos]. */
        fun name(): String {
            val labels = ArrayList<String>()
            var p = pos
            var jumped = false
            var jumps = 0
            while (true) {
                val l = u8(p)
                when {
                    l == 0 -> { p++; break }
                    l and 0xC0 == 0xC0 -> {
                        val target = ((l and 0x3F) shl 8) or u8(p + 1)
                        if (!jumped) pos = p + 2
                        jumped = true
                        check(++jumps < 64) { "loop di compressione" }
                        p = target
                    }
                    else -> {
                        if (p + 1 + l > length) throw IndexOutOfBoundsException()
                        labels += String(data, p + 1, l, Charsets.UTF_8)
                        p += 1 + l
                    }
                }
            }
            if (!jumped) pos = p
            return labels.joinToString(".")
        }
    }
}
