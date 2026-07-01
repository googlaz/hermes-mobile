package com.hermes.app.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.local.entity.LogEntryEntity
import com.hermes.app.data.remote.dto.RunDto
import com.hermes.app.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogUiState(
    val logs: List<LogEntryEntity> = emptyList(),
    val activeRuns: List<RunDto> = emptyList(),
    val isSyncing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    init {
        // Реактивное наблюдение за локальными логами из Room
        logRepository.observeLocalLogs
            .onEach { list -> _state.update { it.copy(logs = list) } }
            .launchIn(viewModelScope)

        loadActiveRuns()
    }

    fun loadActiveRuns() {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, error = null) }
            logRepository.getActiveRuns()
                .onSuccess { list -> _state.update { it.copy(activeRuns = list, isSyncing = false) } }
                .onFailure { err -> _state.update { it.copy(isSyncing = false, error = "Ошибка чтения задач: ${err.message}") } }
        }
    }

    fun cancelRun(runId: String) {
        viewModelScope.launch {
            logRepository.cancelActiveRun(runId)
                .onSuccess { loadActiveRuns() }
                .onFailure { err -> _state.update { it.copy(error = "Не удалось отменить: ${err.message}") } }
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            logRepository.clearLocalLogs()
        }
    }
}
