package com.hermes.app.data.local.dao

import androidx.room.*
import com.hermes.app.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getLogsPaged(limit: Int, offset: Int): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC")
    fun observeAllLogs(): Flow<List<LogEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<LogEntryEntity>)

    @Query("DELETE FROM log_entries")
    suspend fun clearLogs()
}
