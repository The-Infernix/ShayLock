package com.example.shaylock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local, on-device database. Nothing in ShayLock ever leaves the device.
 *
 * Only the per-app configuration and counters live in Room. Global flags (the blocker switch,
 * the PIN hash and the in-flight usage timer) stay in [PinStore] / `SharedPreferences` because
 * they are single values that must be readable before the database is ready.
 */
@Database(
    entities = [TrackedAppEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ShayLockDatabase : RoomDatabase() {

    abstract fun trackedAppDao(): TrackedAppDao

    companion object {
        private const val DATABASE_NAME = "shaylock.db"

        @Volatile
        private var instance: ShayLockDatabase? = null

        /** Returns the process-wide database, creating it on first use. */
        fun get(context: Context): ShayLockDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShayLockDatabase::class.java,
                    DATABASE_NAME,
                ).build().also { instance = it }
            }
    }
}
