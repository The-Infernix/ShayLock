package com.example.shaylock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * Data access for the protected app list.
 *
 * Writes replace the whole table in one transaction. The list is tiny (a handful of apps) and a
 * full snapshot removes every ordering problem that a partial update could introduce when the
 * accessibility watchdog and the UI write at the same time.
 */
@Dao
abstract class TrackedAppDao {

    @Query("SELECT * FROM tracked_apps ORDER BY sortOrder ASC, label ASC")
    abstract suspend fun getAll(): List<TrackedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(apps: List<TrackedAppEntity>)

    @Query("DELETE FROM tracked_apps")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(apps: List<TrackedAppEntity>) {
        clear()
        if (apps.isNotEmpty()) insertAll(apps)
    }
}
