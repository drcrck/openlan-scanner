package io.github.drcrck.openlan.data.repository

import androidx.room.withTransaction
import io.github.drcrck.openlan.data.db.AppDatabase
import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.data.db.NetworkEntity
import io.github.drcrck.openlan.data.db.ScanResultEntity
import io.github.drcrck.openlan.data.db.ScanRunEntity
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.scanner.DeviceType
import io.github.drcrck.openlan.scanner.DiscoveredHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Dispositivo noto + esito dell'ultima scansione. */
data class DeviceWithStatus(
    val device: DeviceEntity,
    val online: Boolean,
    val matchedBy: MatchKind?,
)

data class DeviceList(val devices: List<DeviceWithStatus>, val lastScanAt: Long?)

class DeviceRepository(private val db: AppDatabase) {

    private val networkDao = db.networkDao()
    private val deviceDao = db.deviceDao()
    private val scanDao = db.scanDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDevices(networkKey: String): Flow<DeviceList> {
        val latestResults: Flow<Pair<ScanRunEntity?, List<ScanResultEntity>>> =
            scanDao.observeLatestRun(networkKey).flatMapLatest { run ->
                if (run == null) flowOf(null to emptyList())
                else scanDao.observeResults(run.id).map { run to it }
            }
        return combine(deviceDao.observeForNetwork(networkKey), latestResults) { devices, (run, results) ->
            val byDevice = results.associateBy { it.deviceId }
            DeviceList(
                devices = devices.map { d ->
                    val r = byDevice[d.id]
                    DeviceWithStatus(d, online = r != null, matchedBy = r?.let { runCatching { MatchKind.valueOf(it.matchedBy) }.getOrNull() })
                },
                lastScanAt = run?.finishedAt,
            )
        }
    }

    fun observeDevice(id: Long): Flow<DeviceEntity?> = deviceDao.observe(id)

    suspend fun rename(deviceId: Long, name: String?) {
        deviceDao.rename(deviceId, name?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Salva l'esito di una scansione, associando ogni host a un dispositivo noto o creandone uno nuovo. */
    suspend fun saveScan(info: NetworkInfo, hosts: List<DiscoveredHost>, startedAt: Long) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = networkDao.get(info.key)
            networkDao.upsert(
                NetworkEntity(
                    key = info.key,
                    ssid = info.ssid ?: existing?.ssid,
                    bssid = info.bssid,
                    subnet = info.subnet,
                    firstSeen = existing?.firstSeen ?: now,
                    lastSeen = now,
                )
            )

            val runId = scanDao.insertRun(ScanRunEntity(networkKey = info.key, startedAt = startedAt))
            val available = deviceDao.getForNetwork(info.key).toMutableList()
            val results = ArrayList<ScanResultEntity>(hosts.size)

            for (host in hosts) {
                val (match, kind) = DeviceMatcher.match(host, available)
                val id = if (match != null) {
                    available.remove(match)
                    // Le versioni precedenti attribuivano al gateway il MAC del BSSID (spesso l'access point): si scarta
                    val old = if (match.macSource == "BSSID") match.copy(mac = null, macSource = null, vendor = null) else match
                    deviceDao.update(
                        old.copy(
                            mac = host.mac ?: old.mac,
                            macSource = host.macSource?.name ?: old.macSource,
                            vendor = host.vendor ?: old.vendor,
                            hostname = host.hostname ?: match.hostname,
                            netbiosName = host.netbiosName ?: match.netbiosName,
                            mdnsName = host.mdnsName ?: match.mdnsName,
                            ssdpUdn = host.ssdpUdn ?: match.ssdpUdn,
                            friendlyName = host.friendlyName ?: match.friendlyName,
                            model = host.model ?: match.model,
                            manufacturer = host.manufacturer ?: match.manufacturer,
                            services = host.services.takeIf { it.isNotEmpty() }?.joinToString(",") ?: match.services,
                            // Il tipo rilevato non sovrascrive un tipo già noto con "sconosciuto"
                            deviceType = host.deviceType.takeIf { it != DeviceType.UNKNOWN }?.name ?: match.deviceType,
                            lastIp = host.ip,
                            isGateway = host.isGateway,
                            lastSeen = now,
                        )
                    )
                    match.id
                } else {
                    deviceDao.insert(
                        DeviceEntity(
                            networkKey = info.key,
                            mac = host.mac,
                            macSource = host.macSource?.name,
                            vendor = host.vendor,
                            hostname = host.hostname,
                            netbiosName = host.netbiosName,
                            mdnsName = host.mdnsName,
                            ssdpUdn = host.ssdpUdn,
                            friendlyName = host.friendlyName,
                            model = host.model,
                            manufacturer = host.manufacturer,
                            services = host.services.takeIf { it.isNotEmpty() }?.joinToString(","),
                            deviceType = host.deviceType.name,
                            lastIp = host.ip,
                            isGateway = host.isGateway,
                            isSelf = host.isSelf,
                            firstSeen = now,
                            lastSeen = now,
                        )
                    )
                }
                results += ScanResultEntity(runId, id, host.ip, kind.name)
            }

            scanDao.insertResults(results)
            scanDao.updateRun(
                ScanRunEntity(id = runId, networkKey = info.key, startedAt = startedAt, finishedAt = now, hostCount = hosts.size)
            )
        }
    }
}
