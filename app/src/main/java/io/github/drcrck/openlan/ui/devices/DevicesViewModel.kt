package io.github.drcrck.openlan.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drcrck.openlan.AppContainer
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.data.repository.DeviceList
import io.github.drcrck.openlan.data.repository.DeviceWithStatus
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
)

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
    private var scanJob: Job? = null

    private val deviceList = network.flatMapLatest { n ->
        if (n == null) flowOf(DeviceList(emptyList(), null))
        else container.deviceRepository.observeDevices(n.key)
    }

    val uiState: StateFlow<DevicesUiState> =
        combine(network, deviceList, scanState, apVendor) { n, list, s, ap ->
            DevicesUiState(
                network = n,
                apVendor = ap,
                devices = list.devices.map { it.toRow(n) }
                    .sortedWith(compareByDescending<DeviceRow> { it.online }.thenBy { Ipv4.sortKey(it.ip) }),
                lastScanAt = list.lastScanAt,
                scanning = s.scanning,
                progress = s.progress,
                rangeLimited = s.rangeLimited,
                message = s.message,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesUiState())

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
                throw e
            } catch (e: Exception) {
                scanState.value = ScanState(message = UiMessage(R.string.msg_scan_error, listOf(e.message ?: "?")))
            }
        }
    }

    fun messageShown() {
        scanState.update { it.copy(message = null) }
    }

    private fun DeviceWithStatus.toRow(n: NetworkInfo?): DeviceRow {
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
        )
    }
}
