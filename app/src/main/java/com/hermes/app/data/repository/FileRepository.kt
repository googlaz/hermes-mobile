package com.hermes.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.dto.FileItemDto
import com.hermes.app.data.remote.dto.FileUploadResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
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
     * Возвращает список файлов в активной рабочей директории на ПК (ФТ-4.2)
     */
    suspend fun fetchFilesList(subPath: String? = null): Result<List<FileItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getFiles(subPath)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Не удалось получить список файлов: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Смена рабочей папки бэкенда на ПК (ФТ-4.1)
     */
    suspend fun changeActiveWorkdir(newPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.changeWorkdir(com.hermes.app.data.remote.dto.ChangeWorkdirRequest(newPath))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Не удалось сменить рабочую папку: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить активный путь рабочей директории на ПК (ФТ-4.1)
     */
    suspend fun getActiveWorkdir(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getActiveWorkdir()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Ошибка чтения рабочей папки: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * --- СТРОГОЕ СОБЛЮДЕНИЕ Pitfall 4 (Out Of Memory на 50 файлах) ---
     * Пакетный аплоад файлов до 50 штук с использованием кастомного потокового RequestBody.
     */
    suspend fun uploadMultipleFiles(uris: List<Uri>): Result<FileUploadResponse> = withContext(Dispatchers.IO) {
        try {
            // Ограничение до 50 файлов согласно критериям ФТ-4.3
            val limitedUris = uris.take(50)
            val multipartParts = mutableListOf<MultipartBody.Part>()

            for (uri in limitedUris) {
                val filename = getFileNameFromUri(uri) ?: "upload_${System.currentTimeMillis()}"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                
                // Кастомный, потоковый RequestBody на базе InputStream, исключающий копирование байтов в ОЗУ
                val streamRequestBody = createStreamingRequestBody(uri, mimeType)
                
                val part = MultipartBody.Part.createFormData("files", filename, streamRequestBody)
                multipartParts.add(part)
            }

            val response = apiService.uploadFiles(multipartParts)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Сбой пакетного аплоада: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Скачивание файла с ПК и сохранение его на Android с использованием буфера (ФТ-4.5)
     */
    suspend fun downloadRemoteFile(remoteRelativePath: String, localTargetFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.downloadFile(remoteRelativePath)
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                
                // Стримим входящие байты в файл во избежание OOM
                responseBody.byteStream().use { input ->
                    FileOutputStream(localTargetFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                Result.success(localTargetFile)
            } else {
                Result.failure(Exception("Не удалось загрузить файл: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Вспомогательный метод создания стримингового RequestBody (Pitfall 4)
    private fun createStreamingRequestBody(uri: Uri, mimeType: String): RequestBody {
        return object : RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long {
                // Пытаемся получить реальную длину файла для хедера Content-Length
                return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { 
                    it.length 
                } ?: -1
            }

            override fun writeTo(sink: BufferedSink) {
                val inputStream: InputStream = context.contentResolver.openInputStream(uri) 
                    ?: throw IOException("Не удалось открыть InputStream для: $uri")
                
                inputStream.use { input ->
                    // Okio.source() автоматически стримит из потока байты во избежание просадок памяти
                    sink.writeAll(input.source())
                }
            }
        }
    }

    // Вспомогательный метод декодирования имени файла из Uri
    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = cursor.getString(index)
                }
            }
        }
        if (name == null) {
            val path = uri.path
            val cut = path?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = path.substring(cut + 1)
            }
        }
        return name
    }
}
