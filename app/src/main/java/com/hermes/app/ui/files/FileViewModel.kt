package com.hermes.app.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.remote.dto.FileItemDto
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
    val currentWorkdir: String = "",
    val files: List<FileItemDto> = emptyList(),
    val currentSubPath: String? = null,
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

    init {
        loadWorkdir()
        loadFiles()
    }

    fun loadWorkdir() {
        viewModelScope.launch {
            fileRepository.getActiveWorkdir()
                .onSuccess { path -> _state.update { it.copy(currentWorkdir = path) } }
                .onFailure { err -> _state.update { it.copy(error = "Ошибка чтения папки: ${err.message}") } }
        }
    }

    fun loadFiles(subPath: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(currentSubPath = subPath, error = null) }
            fileRepository.fetchFilesList(subPath)
                .onSuccess { list ->
                    _state.update { it.copy(files = list) }
                }
                .onFailure { err ->
                    _state.update { it.copy(error = "Ошибка чтения файлов на ПК: ${err.message}") }
                }
        }
    }

    fun changeWorkdir(newPath: String) {
        viewModelScope.launch {
            fileRepository.changeActiveWorkdir(newPath)
                .onSuccess {
                    _state.update { it.copy(currentWorkdir = newPath) }
                    loadFiles()
                }
                .onFailure { err ->
                    _state.update { it.copy(error = "Ошибка смены папки: ${err.message}") }
                }
        }
    }

    /**
     * Пакетный аплоад файлов на ПК (ФТ-4.3, до 50 за раз)
     */
    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.size > 50) {
            _state.update { it.copy(error = "Нельзя загрузить более 50 файлов одновременно.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isUploading = true, uploadStatus = "Заливка ${uris.size} файлов...", error = null) }
            fileRepository.uploadMultipleFiles(uris)
                .onSuccess { response ->
                    _state.update { 
                        it.copy(
                            isUploading = false,
                            uploadStatus = "Успешно загружено ${response.uploadedFiles.size} файлов!",
                            error = null
                        ) 
                    }
                    loadFiles(_state.value.currentSubPath) // Обновляем список файлов
                }
                .onFailure { err ->
                    _state.update { it.copy(isUploading = false, error = "Ошибка отправки: ${err.message}", uploadStatus = null) }
                }
        }
    }

    /**
     * Скачивание файла на устройство во внешнюю папку (ФТ-4.5)
     */
    fun downloadFile(remoteRelativePath: String, localTargetDir: File, filename: String) {
        viewModelScope.launch {
            _state.update { it.copy(isDownloading = true, error = null) }
            
            val targetLocalFile = File(localTargetDir, filename)
            fileRepository.downloadRemoteFile(remoteRelativePath, targetLocalFile)
                .onSuccess { file ->
                    _state.update { 
                        it.copy(
                            isDownloading = false,
                            uploadStatus = "Файл сохранен: ${file.name}"
                        ) 
                    }
                }
                .onFailure { err ->
                    _state.update { 
                        it.copy(
                            isDownloading = false,
                            error = "Ошибка скачивания: ${err.message}"
                        ) 
                    }
                }
        }
    }
}
