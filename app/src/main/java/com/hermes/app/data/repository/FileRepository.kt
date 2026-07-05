package com.hermes.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.ChatCompletionResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: HermesApiService
) {
    /**
     * Пакетная отправка файлов в активную сессию чата (ФТ-4.3).
     * Hermes API не имеет отдельного файлового менеджера — файлы отправляются
     * как multipart в POST /api/sessions/{id}/chat.
     */
    suspend fun uploadFilesToSession(
        sessionId: String,
        uris: List<Uri>
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            val limitedUris = uris.take(50)
            val parts = mutableListOf<MultipartBody.Part>()

            for (uri in limitedUris) {
                val filename = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val body = createStreamingRequestBody(uri, mimeType)
                parts.add(MultipartBody.Part.createFormData("files", filename, body))
            }

            // Best-effort: /chat ожидает поле "message"; добавляем текстовую часть,
            // чтобы чистая multipart-загрузка файлов не отклонялась с 400.
            val messagePart = RequestBody.create(
                "text/plain".toMediaTypeOrNull(),
                "Загружены файлы: " + limitedUris.mapNotNull { getFileNameFromUri(it) }.joinToString(", ")
            )

            val response = apiService.uploadFilesToSession(sessionId, parts, messagePart)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Сбой загрузки файлов: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Скачивание вложения из сообщения (ФТ-4.5)
     */
    suspend fun downloadAttachment(
        sessionId: String,
        messageId: String,
        localTargetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.downloadAttachment(sessionId, messageId)
            if (response.isSuccessful && response.body() != null) {
                streamToFile(response.body()!!, localTargetFile)
                Result.success(localTargetFile)
            } else {
                Result.failure(Exception("Не удалось скачать файл: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun streamToFile(body: ResponseBody, target: File) {
        body.byteStream().use { input ->
            FileOutputStream(target).use { output ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) output.write(buf, 0, n)
            }
        }
    }

    private fun createStreamingRequestBody(uri: Uri, mimeType: String): RequestBody =
        object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength(): Long =
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
            override fun writeTo(sink: BufferedSink) {
                val stream: InputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть: $uri")
                stream.use { sink.writeAll(it.source()) }
            }
        }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return c.getString(idx)
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
