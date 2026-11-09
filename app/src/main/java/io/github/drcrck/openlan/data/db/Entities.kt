package io.github.drcrck.openlan.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Rete conosciuta. La chiave è il BSSID, o gateway+subnet se il BSSID non è leggibile. */
@Entity(tableName = "networks")
data class NetworkEntity(
    @PrimaryKey val key: String,
    val ssid: String?,
    val bssid: String?,
    val subnet: String,
    val firstSeen: Long,
    val lastSeen: Long,
)

/**
 * Dispositivo visto su una rete. Il MAC può mancare: l'identità è l'id interno,
 * il riconoscimento tra scansioni lo fa [io.github.drcrck.openlan.data.repository.DeviceMatcher].
 */
@Entity(
    tableName = "devices",
    indices = [Index("networkKey"), Index("networkKey", "mac")],
)
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkKey: String,
    val mac: String? = null,
    val macSource: String? = null,
    val vendor: String? = null,
    val hostname: String? = null,
    val netbiosName: String? = null,
    val mdnsName: String? = null,
    val ssdpUdn: String? = null,
    val lastIp: String,
    val customName: String? = null,
    val notes: String? = null,
    val trusted: Boolean = false,
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val firstSeen: Long,
    val lastSeen: Long,
    // Versione 2: dati da mDNS / UPnP
    val friendlyName: String? = null,
    val model: String? = null,
    val manufacturer: String? = null,
    /** Servizi mDNS separati da virgola. */
    val services: String? = null,
    /** Nome dell'enum [io.github.drcrck.openlan.scanner.DeviceType]. */
    val deviceType: String? = null,
)

@Entity(tableName = "scan_runs", indices = [Index("networkKey")])
data class ScanRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val networkKey: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val hostCount: Int = 0,
)

/** Presenza di un dispositivo in una scansione. [matchedBy] = come è stato riconosciuto (MAC, NETBIOS, IP...). */
@Entity(
    tableName = "scan_results",
    primaryKeys = ["runId", "deviceId"],
    indices = [Index("deviceId")],
)
data class ScanResultEntity(
    val runId: Long,
    val deviceId: Long,
    val ip: String,
    val matchedBy: String,
)
