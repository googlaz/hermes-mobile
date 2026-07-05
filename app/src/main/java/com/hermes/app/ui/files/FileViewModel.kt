package com.hermes.app.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FileUiState(
    val isUploading: Boolean = false,
    val isDownloading: Boolean = false,
    val uploadStatus: String? = null,
    val error: String? = null
)

@HiltViewModel
class FileViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FileUiState())
    val state: StateFlow<FileUiState> = _state.asStateFlow()

    // Активная сессия для прикрепления файлов
    private var currentSessionId: String = ""

    fun setSession(sessionId: String) {
        currentSessionId = sessionId
    }

    /**
     * Загрузка файлов на ПК через sidecar POST /upload. Sidecar сохраняет файлы
     * в кэш документов Hermes и возвращает абсолютные пути. Здесь показываем краткий
     * статус; основной сценарий работы с файлами — прикрепление прямо в чате.
     */
    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadStatus = "Загрузка файлов…", error = null) }
            val names = mutableListOf<String>()
            var failed: String? = null
            for (uri in uris) {
                fileRepository.uploadToSidecar(uri)
                    .onSuccess { resp -> names.add(resp.filename ?: "файл") }
                    .onFailure { err -> failed = err.message }
            }
            if (failed != null) {
                _state.update { it.copy(isUploading = false, error = failed) }
            } else {
                _state.update {
                    it.copy(
                        isUploading = false,
                        uploadStatus = "Загружено на ПК: ${names.joinToString(", ")}. Прикрепите файлы в чате, чтобы агент их разобрал."
                    )
                }
            }
        }
    }

    /**
     * Скачивание вложения из сообщения на устройство (ФТ-4.5)
     */
    fun downloadFile(messageId: String, localTargetDir: File, filename: String) {
        if (currentSessionId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true, error = null) }
            val target = File(localTargetDir, filename)
            fileRepository.downloadAttachment(currentSessionId, messageId, target)
                .onSuccess { file ->
                    _state.update { it.copy(isDownloading = false, uploadStatus = "Сохранено: ${file.name}") }
                }
                .onFailure { err ->
                    _state.update { it.copy(isDownloading = false, error = "Ошибка скачивания: ${err.message}") }
                }
        }
    }

    // Stub для совместимости с FileManagerScreen — смена рабочей директории
    // реализуется через чат-команду, не через отдельный API
    fun changeWorkdir(newPath: String) {
        _state.update { it.copy(uploadStatus = "Команда отправляется через чат...") }
    }
}
