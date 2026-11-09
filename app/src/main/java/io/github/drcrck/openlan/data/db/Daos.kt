package io.github.drcrck.openlan.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {
    @Upsert
    suspend fun upsert(network: NetworkEntity)

    @Query("SELECT * FROM networks WHERE `key` = :key")
    suspend fun get(key: String): NetworkEntity?
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices WHERE networkKey = :networkKey")
    fun observeForNetwork(networkKey: String): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE networkKey = :networkKey")
    suspend fun getForNetwork(networkKey: String): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE id = :id")
    fun observe(id: Long): Flow<DeviceEntity?>

    @Insert
    suspend fun insert(device: DeviceEntity): Long

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("UPDATE devices SET customName = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE devices SET openPorts = :ports, portsScannedAt = :at WHERE id = :id")
    suspend fun savePorts(id: Long, ports: String, at: Long)
}

@Dao
interface ScanDao {
    @Insert
    suspend fun insertRun(run: ScanRunEntity): Long

    @Update
    suspend fun updateRun(run: ScanRunEntity)

    @Insert
    suspend fun insertResults(results: List<ScanResultEntity>)

    @Query(
        "SELECT * FROM scan_runs WHERE networkKey = :networkKey AND finishedAt IS NOT NULL " +
            "ORDER BY startedAt DESC LIMIT 1"
    )
    fun observeLatestRun(networkKey: String): Flow<ScanRunEntity?>

    @Query("SELECT * FROM scan_results WHERE runId = :runId")
    fun observeResults(runId: Long): Flow<List<ScanResultEntity>>
}
