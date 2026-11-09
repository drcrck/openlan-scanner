package io.github.drcrck.openlan.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NetworkEntity::class, DeviceEntity::class, ScanRunEntity::class, ScanResultEntity::class],
    version = 2,
    exportSchema = true,
    // v2: nuove colonne facoltative su devices (Room genera la migrazione dallo schema in app/schemas)
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun deviceDao(): DeviceDao
    abstract fun scanDao(): ScanDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "netscan.db").build()
    }
}
