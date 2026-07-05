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
     * Загрузка файлов ВРЕМЕННО не поддерживается сервером Hermes:
     * /chat принимает только JSON (текст и image_url-ссылки), multipart отклоняется
     * с unsupported_content_type. Показываем понятное уведомление вместо битого запроса.
     */
    fun uploadFiles(uris: List<Uri>) {
        _state.update {
            it.copy(
                isUploading = false,
                uploadStatus = null,
                error = "Загрузка файлов пока не поддерживается сервером Hermes (только текст и изображения-ссылки)."
            )
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
