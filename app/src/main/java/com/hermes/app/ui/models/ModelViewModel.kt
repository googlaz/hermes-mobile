package com.hermes.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.remote.dto.SidecarModelDto
import com.hermes.app.data.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelUiState(
    val models: List<SidecarModelDto> = ModelRepository.FALLBACK_MODELS, // не даём экрану быть пустым
    val currentModel: String? = null,
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

            val modelsResult = modelRepository.getModels()
            val currentResult = modelRepository.getCurrentModel()

            val models = modelsResult.getOrDefault(ModelRepository.FALLBACK_MODELS)
            // Текущую берём из /current, иначе из флага current в списке моделей
            val current = currentResult.getOrNull()?.model
                ?: models.firstOrNull { it.current }?.id

            _state.update {
                it.copy(
                    models = models,
                    currentModel = current ?: it.currentModel,
                    isLoading = false
                )
            }
        }
    }

    fun switchActiveModel(modelId: String, provider: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, successMessage = null) }
            modelRepository.switchModel(modelId, provider)
                .onSuccess {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            currentModel = modelId,
                            successMessage = "Модель переключена на $modelId. Применится со следующего сообщения."
                        )
                    }
                    // Обновляем текущую модель с сервера для точности
                    modelRepository.getCurrentModel().onSuccess { cur ->
                        cur.model?.let { m -> _state.update { s -> s.copy(currentModel = m) } }
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
