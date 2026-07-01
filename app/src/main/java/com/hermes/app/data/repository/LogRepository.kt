package com.hermes.app.data.repository

import com.hermes.app.data.local.dao.LogEntryDao
import com.hermes.app.data.local.entity.LogEntryEntity
import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.RunDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val logEntryDao: LogEntryDao,
    private val apiService: HermesApiService
) {
    // Реактивное наблюдение над сохраненными локально логами для терминала (ФТ-5.1)
    val observeLocalLogs: Flow<List<LogEntryEntity>> = logEntryDao.observeAllLogs()

    /**
     * Стягивает свежие логи с API сервера и кэширует их в Room (ФТ-5.1)
     */
    suspend fun syncLogsFromServer(limit: Int = 100, offset: Int = 0): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getLogs(limit, offset)
            if (response.isSuccessful && response.body() != null) {
                val dtoList = response.body()!!
                val entities = dtoList.map { dto ->
                    LogEntryEntity(
                        runId = dto.runId,
                        level = dto.level,
                        tag = dto.tag,
                        message = dto.message,
                        timestamp = dto.timestamp
                    )
                }
                logEntryDao.insertLogs(entities)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Не удалось синхронизировать логи: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить активные фоновые задачи (runs) с ПК (ФТ-5.2)
     */
    suspend fun getActiveRuns(): Result<List<RunDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getActiveRuns()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Не удалось получить runs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отмена выполнения фоновой задачи на ПК (ФТ-5.2)
     */
    suspend fun cancelActiveRun(runId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.cancelRun(runId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Не удалось отменить задачу: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
