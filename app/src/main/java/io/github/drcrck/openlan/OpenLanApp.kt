package io.github.drcrck.openlan

import android.app.Application
import android.content.Context
import io.github.drcrck.openlan.data.db.AppDatabase
import io.github.drcrck.openlan.data.network.NetworkInfoProvider
import io.github.drcrck.openlan.data.oui.OuiLookup
import io.github.drcrck.openlan.data.repository.DeviceRepository
import io.github.drcrck.openlan.scanner.HostProber
import io.github.drcrck.openlan.scanner.MdnsDiscovery
import io.github.drcrck.openlan.scanner.NetbiosDiscovery
import io.github.drcrck.openlan.scanner.PingTool
import io.github.drcrck.openlan.scanner.PortScanner
import io.github.drcrck.openlan.scanner.ReverseDns
import io.github.drcrck.openlan.scanner.ScanEngine
import io.github.drcrck.openlan.scanner.SsdpDiscovery
import io.github.drcrck.openlan.scanner.TracerouteTool

class OpenLanApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Collegamento manuale delle dipendenze (al posto di Hilt, finché l'app è piccola). */
class AppContainer(val context: Context) {
    private val database by lazy { AppDatabase.create(context) }
    val oui = OuiLookup(context)

    private val reverseDns = ReverseDns()

    val networkInfo = NetworkInfoProvider(context)
    val deviceRepository by lazy { DeviceRepository(database) }
    val scanEngine = ScanEngine(HostProber(), NetbiosDiscovery(), MdnsDiscovery(context), SsdpDiscovery(), reverseDns, oui)
    val portScanner = PortScanner()
    val pingTool = PingTool()
    val traceroute = TracerouteTool(reverseDns)

    /** Testo diagnostico dell'ultima scansione (solo in memoria). */
    @Volatile var lastDiagnostics: String? = null
}
