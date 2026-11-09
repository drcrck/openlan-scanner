package io.github.drcrck.openlan.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drcrck.openlan.AppContainer
import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.data.repository.MatchKind
import io.github.drcrck.openlan.scanner.KnownPorts
import io.github.drcrck.openlan.scanner.OpenPort
import io.github.drcrck.openlan.ui.common.roleTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import io.github.drcrck.openlan.scanner.PortKind
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import io.github.drcrck.openlan.scanner.PingStats
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortScanState(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val open: List<OpenPort> = emptyList(),
    val finished: Boolean = false,
    val extended: Boolean = false,
    val durationMs: Long? = null,
    /** true = porte ricordate da una scansione precedente (non ancora rifatta in questa sessione). */
    val remembered: Boolean = false,
    val scannedAt: Long? = null,
)

data class DeviceDetail(
    val device: DeviceEntity,
    val online: Boolean,
    val tags: List<io.github.drcrck.openlan.ui.common.Role>,
    /** Come è stato riconosciuto nell'ultima scansione (null se non era online). */
    val matchedBy: MatchKind?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceDetailViewModel(private val container: AppContainer, deviceId: Long) : ViewModel() {

    private val network = container.networkInfo.current()

    val detail: StateFlow<DeviceDetail?> = container.deviceRepository.observeDevice(deviceId)
        .flatMapLatest { d ->
            if (d == null) flowOf(null)
            else container.deviceRepository.observeDevices(d.networkKey).map { list ->
                val s = list.devices.firstOrNull { it.device.id == deviceId }
                val dev = s?.device ?: d
                val online = s?.online == true
                DeviceDetail(dev, online, roleTags(dev, online, network?.takeIf { it.key == dev.networkKey }), s?.matchedBy)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Ping continuo (1 al secondo) verso l'IP del dispositivo. Gira solo mentre la scheda è visibile:
     * con WhileSubscribed(0) il ciclo si ferma appena la UI smette di osservarlo.
     */
    val ping: StateFlow<PingStats> = detail
        .map { it?.device?.lastIp }
        .distinctUntilChanged()
        .flatMapLatest { ip ->
            if (ip == null) flowOf(PingStats())
            else flow {
                var stats = PingStats()
                emit(stats)
                while (true) {
                    val start = System.currentTimeMillis()
                    stats = stats.add(container.pingTool.ping(ip))
                    emit(stats)
                    delay((1000 - (System.currentTimeMillis() - start)).coerceAtLeast(0))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), PingStats())

    private val _ports = MutableStateFlow(PortScanState())

    /** Porte della scansione in corso/appena fatta; altrimenti quelle ricordate dall'ultima volta. */
    val ports: StateFlow<PortScanState> = combine(_ports, detail) { p, d ->
        val saved = d?.device?.openPorts
        if (p.running || p.finished || saved == null) p
        else p.copy(
            open = saved.split(",").mapNotNull { it.trim().toIntOrNull() }.map { port ->
                val k = KnownPorts.byPort[port]
                OpenPort(port, k?.name, isHttp = k?.kind == PortKind.HTTP, isTls = k?.kind == PortKind.HTTPS)
            },
            remembered = true,
            scannedAt = d.device.portsScannedAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PortScanState())
    private var job: Job? = null

    fun scanPorts(extended: Boolean) {
        val ip = detail.value?.device?.lastIp ?: return
        job?.cancel()
        val list = if (extended) KnownPorts.extended else KnownPorts.common
        job = viewModelScope.launch {
            val start = System.currentTimeMillis()
            _ports.value = PortScanState(running = true, total = list.size, extended = extended)
            val open = container.portScanner.scan(ip, list) { done, total, open ->
                _ports.update { it.copy(done = done, total = total, open = open) }
            }
            _ports.value = PortScanState(
                done = list.size, total = list.size, open = open, finished = true,
                extended = extended, durationMs = System.currentTimeMillis() - start,
                scannedAt = System.currentTimeMillis(),
            )
            detail.value?.device?.id?.let { id -> container.deviceRepository.savePorts(id, open.map { it.port }) }
        }
    }

    fun stopPortScan() {
        job?.cancel()
        _ports.update { it.copy(running = false) }
    }

    fun rename(name: String) {
        val id = detail.value?.device?.id ?: return
        viewModelScope.launch { container.deviceRepository.rename(id, name) }
    }
}
