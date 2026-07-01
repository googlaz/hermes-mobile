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
    // Реактивное наблюдение за локально закэшированными логами (ФТ-5.1)
    val observeLocalLogs: Flow<List<LogEntryEntity>> = logEntryDao.observeAllLogs()

    /**
     * Получить список активных задач (jobs) с ПК (ФТ-5.2)
     */
    suspend fun getActiveRuns(): Result<List<RunDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getActiveJobs()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Не удалось получить задачи: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Отмена выполняющейся задачи на ПК (ФТ-5.2)
     */
    suspend fun cancelActiveRun(runId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.cancelJob(runId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Не удалось отменить задачу: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Hermes API не имеет эндпоинта /api/logs — логи получаем через
     * сообщения сессий. Этот метод просто очищает локальный кэш.
     */
    suspend fun clearLocalLogs(): Unit = withContext(Dispatchers.IO) {
        logEntryDao.clearAll()
    }
}
