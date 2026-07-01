package com.hermes.app.data.remote

import com.hermes.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface HermesApiService {

    // === 1. Подключение и статус ===
    @GET("api/health")
    suspend fun checkHealth(): Response<HealthStatusDto>


    // === 2. Чат и сессии ===
    @GET("api/sessions")
    suspend fun getSessions(): Response<List<ChatSessionDto>>

    @POST("api/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<ChatSessionDto>

    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(
        @Path("id") sessionId: String
    ): Response<Unit>

    @GET("api/sessions/{id}/messages")
    suspend fun getMessages(
        @Path("id") sessionId: String
    ): Response<List<ChatMessageDto>>

    @POST("api/sessions/{id}/messages")
    suspend fun sendMessage(
        @Path("id") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>


    // === 3. Управление моделями ===
    @GET("api/models")
    suspend fun getAvailableModels(): Response<List<ModelDto>>

    @POST("api/sessions/{id}/model")
    suspend fun switchModel(
        @Path("id") sessionId: String,
        @Body request: SwitchModelRequest
    ): Response<ChatSessionDto>


    // === 4. Файловый менеджер ===
    @GET("api/workdir")
    suspend fun getActiveWorkdir(): Response<String>

    @POST("api/workdir")
    suspend fun changeWorkdir(
        @Body request: ChangeWorkdirRequest
    ): Response<Unit>

    @GET("api/files")
    suspend fun getFiles(
        @Query("path") path: String? = null
    ): Response<List<FileItemDto>>

    @Multipart
    @POST("api/files/upload")
    suspend fun uploadFiles(
        @Part files: List<MultipartBody.Part>
    ): Response<FileUploadResponse>

    @Streaming
    @GET("api/files/download")
    suspend fun downloadFile(
        @Query("path") relativePath: String
    ): Response<ResponseBody>


    // === 5. Логи и задачи ===
    @GET("api/logs")
    suspend fun getLogs(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0
    ): Response<List<LogEntryEntityDto>> // Вспомогательный DTO объект для логов, замапим в Entity

    @GET("api/runs")
    suspend fun getActiveRuns(): Response<List<RunDto>>

    @POST("api/runs/{id}/cancel")
    suspend fun cancelRun(
        @Path("id") runId: String
    ): Response<Unit>
}

// Вспомогательный DTO для сырых логов из бэкенда
data class LogEntryEntityDto(
    val runId: String?,
    val level: String,
    val tag: String,
    val message: String,
    val timestamp: Long
)
