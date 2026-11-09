package io.github.drcrck.openlan.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drcrck.openlan.AppContainer
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.data.repository.DeviceList
import io.github.drcrck.openlan.data.repository.DeviceWithStatus
import io.github.drcrck.openlan.data.repository.MatchKind
import io.github.drcrck.openlan.scanner.DeviceType
import io.github.drcrck.openlan.scanner.Ipv4
import io.github.drcrck.openlan.scanner.ScanProgress
import io.github.drcrck.openlan.ui.common.Role
import io.github.drcrck.openlan.ui.common.UiMessage
import io.github.drcrck.openlan.ui.common.displayName
import io.github.drcrck.openlan.ui.common.roleTags
import io.github.drcrck.openlan.ui.common.type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Riga della lista dispositivi, già pronta per la UI. */
data class DeviceRow(
    val id: Long,
    val title: String,
    val type: DeviceType,
    val ip: String,
    val device: DeviceEntity,
    val subtitle: String?,
    val tags: List<Role>,
    val online: Boolean,
    val isSelf: Boolean,
    /** Ha un nome o un tipo riconosciuto (altrimenti è solo un IP). */
    val identified: Boolean,
    /** Comparso per la prima volta nell'ultima scansione. */
    val isNew: Boolean,
) {
    val isInfra: Boolean get() = online && tags.isNotEmpty()
}

/** Filtri rapidi della lista (chip del riepilogo). */
enum class ListFilter {
    INFRA, NEW, SERVER, NAS, COMPUTER, PRINTER, MEDIA, MOBILE, CAMERA, IOT, UNIDENTIFIED, OFFLINE;

    fun matches(r: DeviceRow): Boolean = when (this) {
        INFRA -> r.isInfra
        NEW -> r.isNew
        OFFLINE -> !r.online
        UNIDENTIFIED -> r.online && !r.identified
        else -> r.online && r.identified && categoryOf(r.type) == this
    }

    companion object {
        fun categoryOf(t: DeviceType): ListFilter? = when (t) {
            DeviceType.SERVER -> SERVER
            DeviceType.NAS -> NAS
            DeviceType.COMPUTER -> COMPUTER
            DeviceType.PRINTER -> PRINTER
            DeviceType.TV, DeviceType.SPEAKER, DeviceType.CONSOLE -> MEDIA
            DeviceType.PHONE, DeviceType.TABLET -> MOBILE
            DeviceType.CAMERA -> CAMERA
            DeviceType.IOT -> IOT
            DeviceType.ROUTER, DeviceType.UNKNOWN -> null
        }
    }
}

/** Gruppi della lista quando non c'è né ricerca né filtro. */
data class DeviceSections(
    val infra: List<DeviceRow> = emptyList(),
    val identified: List<DeviceRow> = emptyList(),
    val unidentified: List<DeviceRow> = emptyList(),
    val offline: List<DeviceRow> = emptyList(),
)

data class DevicesUiState(
    val network: NetworkInfo? = null,
    /** Produttore dell'access point Wi-Fi (dal BSSID). */
    val apVendor: String? = null,
    val devices: List<DeviceRow> = emptyList(),
    val lastScanAt: Long? = null,
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    val rangeLimited: Boolean = false,
    val message: UiMessage? = null,
    val query: String = "",
    val filter: ListFilter? = null,
    /** Conteggi per i chip del riepilogo (solo quelli > 0). */
    val counts: Map<ListFilter, Int> = emptyMap(),
    val sections: DeviceSections = DeviceSections(),
    /** Risultati piatti quando ricerca o filtro sono attivi. */
    val results: List<DeviceRow>? = null,
)

private data class ListState(val query: String = "", val filter: ListFilter? = null)

private data class ScanState(
    val scanning: Boolean = false,
    val progress: ScanProgress? = null,
    val rangeLimited: Boolean = false,
    val message: UiMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModel(private val container: AppContainer) : ViewModel() {

    private val network = MutableStateFlow<NetworkInfo?>(null)
    private val apVendor = network.map { n -> n?.bssid?.let { container.oui.vendorFor(it) } }
    private val scanState = MutableStateFlow(ScanState())
    private val listState = MutableStateFlow(ListState())
    private var scanJob: Job? = null

    private val deviceList = network.flatMapLatest { n ->
        if (n == null) flowOf(DeviceList(emptyList(), null))
        else container.deviceRepository.observeDevices(n.key)
    }

    val uiState: StateFlow<DevicesUiState> =
        combine(network, deviceList, scanState, apVendor, listState) { n, list, s, ap, ls ->
            // "Nuovo" ha senso solo dalla seconda scansione: alla prima tutto sarebbe nuovo
            val firstScanAt = list.devices.minOfOrNull { it.device.firstSeen }
            val rows = list.devices.map { it.toRow(n, firstScanAt) }
                .sortedWith(compareByDescending<DeviceRow> { it.online }.thenBy { Ipv4.sortKey(it.ip) })
            val counts = ListFilter.entries.associateWith { f -> rows.count { f.matches(it) } }.filterValues { it > 0 }
            val q = ls.query.trim()
            val filtering = q.isNotEmpty() || ls.filter != null
            DevicesUiState(
                network = n,
                apVendor = ap,
                devices = rows,
                lastScanAt = list.lastScanAt,
                scanning = s.scanning,
                progress = s.progress,
                rangeLimited = s.rangeLimited,
                message = s.message,
                query = ls.query,
                filter = ls.filter,
                counts = counts,
                sections = if (filtering) DeviceSections() else DeviceSections(
                    // Infrastruttura: gateway, poi DNS/DHCP, poi questo telefono
                    infra = rows.filter { it.isInfra }.sortedBy { r ->
                        when {
                            Role.GATEWAY in r.tags -> 0
                            Role.DNS in r.tags || Role.DHCP in r.tags -> 1
                            else -> 2
                        }
                    },
                    identified = rows.filter { it.online && !it.isInfra && it.identified },
                    unidentified = rows.filter { it.online && !it.isInfra && !it.identified },
                    offline = rows.filter { !it.online },
                ),
                results = if (!filtering) null else rows.filter { r ->
                    (ls.filter == null || ls.filter.matches(r)) && (q.isEmpty() || r.matchesQuery(q))
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

    fun setQuery(q: String) = listState.update { it.copy(query = q) }

    /** Toccare di nuovo lo stesso chip lo toglie. */
    fun toggleFilter(f: ListFilter) = listState.update { it.copy(filter = if (it.filter == f) null else f) }

    fun clearFilters() = listState.update { ListState() }

    fun stopScan() {
        scanJob?.cancel()
    }

    init {
        refreshNetwork()
    }

    /** Rilegge la rete corrente (da chiamare anche dopo aver concesso i permessi). */
    fun refreshNetwork() {
        network.value = container.networkInfo.current()
    }

    fun scan() {
        if (scanJob?.isActive == true) return
        refreshNetwork()
        val info = network.value
        if (info == null) {
            scanState.update { it.copy(message = UiMessage(R.string.msg_no_network)) }
            return
        }
        scanJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            scanState.value = ScanState(scanning = true)
            try {
                val outcome = container.scanEngine.scan(info) { p ->
                    scanState.update { it.copy(progress = p) }
                }
                container.lastDiagnostics = outcome.diagnostics.text()
                container.deviceRepository.saveScan(info, outcome.hosts, started)
                val withMac = outcome.hosts.count { it.mac != null }
                scanState.value = ScanState(
                    rangeLimited = outcome.range.limited,
                    message = UiMessage(R.string.msg_scan_done, listOf(outcome.hosts.size, withMac)),
                )
            } catch (e: CancellationException) {
                scanState.value = ScanState(message = UiMessage(R.string.msg_scan_stopped))
                throw e
            } catch (e: Exception) {
                container.lastDiagnostics = "Scan failed: ${e::class.java.simpleName}: ${e.message}"
                scanState.value = ScanState(
                    message = UiMessage(
                        if (e is java.io.IOException) R.string.msg_scan_error_network else R.string.msg_scan_error
                    ),
                )
            }
        }
    }

    fun messageShown() {
        scanState.update { it.copy(message = null) }
    }

    private fun DeviceRow.matchesQuery(q: String): Boolean =
        listOfNotNull(title, ip, subtitle, device.mac, device.vendor, device.hostname, device.netbiosName, device.mdnsName, device.model)
            .any { it.contains(q, ignoreCase = true) }

    private fun DeviceWithStatus.toRow(n: NetworkInfo?, firstScanAt: Long?): DeviceRow {
        val d = device
        val title = d.displayName(container.context.getString(R.string.this_phone))
        val subtitle = listOfNotNull(d.model, d.vendor, d.manufacturer)
            .map { it.trim() }
            .distinct()
            .filter { it != title }
            .take(2)
            .joinToString(" · ")
            .ifEmpty { null }
        return DeviceRow(
            id = d.id,
            title = title,
            type = d.type,
            ip = d.lastIp,
            device = d,
            subtitle = subtitle,
            tags = roleTags(d, online, n),
            online = online,
            isSelf = d.isSelf,
            identified = title != d.lastIp || d.type != DeviceType.UNKNOWN,
            isNew = online && matchedBy == MatchKind.NEW && firstScanAt != null && d.firstSeen > firstScanAt + 60_000,
        )
    }
}
