package com.hermes.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelUiState(
    val models: List<String> = ModelRepository.FALLBACK_MODELS, // не даём экрану быть пустым
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ModelViewModel @Inject constructor(
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ModelUiState())
    val state: StateFlow<ModelUiState> = _state.asStateFlow()

    fun loadModels() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            modelRepository.getAvailableModels()
                .onSuccess { list ->
                    // list всегда содержит как минимум fallback-модели
                    _state.update { it.copy(models = list, isLoading = false) }
                }
                .onFailure {
                    // Даже при сбое показываем fallback-список
                    _state.update { it.copy(models = ModelRepository.FALLBACK_MODELS, isLoading = false) }
                }
        }
    }

    fun switchActiveModel(sessionId: String, modelId: String, provider: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
            modelRepository.switchModelForSession(sessionId, modelId, provider)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Модель успешно переключена на $modelId!"
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Не удалось сменить модель: ${err.message}"
                        )
                    }
                }
        }
    }
}
