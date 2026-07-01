package com.hermes.app.data.remote

import com.hermes.app.data.remote.dto.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface HermesApiService {

    // === 1. Подключение и статус ===
    // Реальный эндпоинт Hermes API Server: GET /health
    @GET("health")
    suspend fun checkHealth(): Response<HealthStatusDto>

    // Список моделей: GET /v1/models
    @GET("v1/models")
    suspend fun getAvailableModels(): Response<ModelsResponse>


    // === 2. Чат и сессии ===
    // GET /api/sessions
    @GET("api/sessions")
    suspend fun getSessions(): Response<List<ChatSessionDto>>

    // POST /api/sessions
    @POST("api/sessions")
    suspend fun createSession(
        @Body request: CreateSessionRequest
    ): Response<ChatSessionDto>

    // DELETE /api/sessions/{id}
    @DELETE("api/sessions/{id}")
    suspend fun deleteSession(
        @Path("id") sessionId: String
    ): Response<Unit>

    // GET /api/sessions/{id}/messages
    @GET("api/sessions/{id}/messages")
    suspend fun getMessages(
        @Path("id") sessionId: String
    ): Response<List<ChatMessageDto>>

    // POST /api/sessions/{id}/chat  (не-стриминговый ответ)
    @POST("api/sessions/{id}/chat")
    suspend fun sendMessage(
        @Path("id") sessionId: String,
        @Body request: SendMessageRequest
    ): Response<ChatMessageDto>


    // === 3. Задачи / Jobs (аналог runs) ===
    // GET /api/jobs
    @GET("api/jobs")
    suspend fun getActiveJobs(): Response<List<RunDto>>

    // DELETE /api/jobs/{id}
    @DELETE("api/jobs/{id}")
    suspend fun cancelJob(
        @Path("id") jobId: String
    ): Response<Unit>


    // === 4. Файловый менеджер (кастомные эндпоинты — реализуются через chat) ===
    @Multipart
    @POST("api/sessions/{id}/chat")
    suspend fun uploadFiles(
        @Path("id") sessionId: String,
        @Part files: List<MultipartBody.Part>
    ): Response<ChatMessageDto>

    @Streaming
    @GET("api/sessions/{id}/messages/{msgId}/attachment")
    suspend fun downloadFile(
        @Path("id") sessionId: String,
        @Path("msgId") messageId: String
    ): Response<ResponseBody>
}


