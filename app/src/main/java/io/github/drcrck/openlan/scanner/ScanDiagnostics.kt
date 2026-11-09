package io.github.drcrck.openlan.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro di cosa è successo durante una scansione, protocollo per protocollo.
 * Serve a capire perché un dato (es. il MAC) manca: nessuna risposta, errore di rete o dato non pubblicato.
 */
class ScanDiagnostics {
    private val sections = LinkedHashMap<String, MutableList<String>>()
    private val started = System.currentTimeMillis()

    fun log(section: String, message: String) {
        synchronized(sections) { sections.getOrPut(section) { mutableListOf() } += message }
    }

    fun text(): String = synchronized(sections) {
        buildString {
            appendLine("OpenLAN Scanner · diagnostica scansione del " +
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ITALY).format(Date(started)))
            sections.forEach { (name, lines) ->
                appendLine()
                appendLine("== $name ==")
                lines.forEach { appendLine(it) }
            }
        }
    }
}
