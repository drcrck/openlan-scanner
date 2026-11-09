package io.github.drcrck.openlan.data.oui

import android.content.Context
import io.github.drcrck.openlan.scanner.MacAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Produttore da MAC usando assets/oui.txt (derivato dal registro IEEE tramite il file "manuf" di Wireshark).
 * Formato: HEX<tab>Nome, con HEX di 6 (/24), 7 (/28) o 9 (/36) cifre.
 * Il file viene caricato in memoria alla prima richiesta (~60.000 voci).
 */
class OuiLookup(private val context: Context) {

    private val mutex = Mutex()
    @Volatile private var table: Map<String, String>? = null

    suspend fun vendorFor(mac: String?): String? {
        val m = MacAddress.normalize(mac) ?: return null
        if (MacAddress.isLocallyAdministered(m)) return null
        val t = load()
        val hex = MacAddress.hex(m)
        return t[hex.take(9)] ?: t[hex.take(7)] ?: t[hex.take(6)]
    }

    private suspend fun load(): Map<String, String> {
        table?.let { return it }
        return mutex.withLock {
            table ?: withContext(Dispatchers.IO) {
                val map = HashMap<String, String>(80_000)
                context.assets.open("oui.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val tab = line.indexOf('\t')
                        if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
                    }
                }
                map
            }.also { table = it }
        }
    }
}
