package com.hermes.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.app.data.local.SecurePreferences
import com.hermes.app.data.remote.HermesApiService
import com.hermes.app.data.remote.TailscaleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences,
    private val tailscaleManager: TailscaleManager,
    private val apiService: HermesApiService
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                host = securePreferences.tailscaleHost ?: "",
                port = securePreferences.serverPort.toString(),
                token = securePreferences.apiServerKey ?: "",
                workdir = securePreferences.workdir ?: ""
            )
        }
        // Авточек только если IP уже настроен
        if (!securePreferences.tailscaleHost.isNullOrBlank()) {
            checkConnection()
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnHostChanged -> {
                _state.update { it.copy(host = event.host, isHostValid = tailscaleManager.isTailscaleAddressValid(event.host)) }
            }
            is SettingsEvent.OnPortChanged -> {
                _state.update { it.copy(port = event.port) }
            }
            is SettingsEvent.OnTokenChanged -> {
                _state.update { it.copy(token = event.token) }
            }
            is SettingsEvent.OnWorkdirChanged -> _state.update { it.copy(workdir = event.workdir) }
            is SettingsEvent.OnSaveClicked -> saveSettings()
            is SettingsEvent.OnCheckConnectionTriggered -> {
                checkConnection()
            }
        }
    }

    private fun saveSettings() {
        val currentState = _state.value
        if (!tailscaleManager.isTailscaleAddressValid(currentState.host)) {
            _state.update { it.copy(errorMessage = "Некорректный Tailscale IP-адрес (подсеть 100.x.y.z) или MagicDNS хост") }
            return
        }

        val portInt = currentState.port.toIntOrNull() ?: 8642

        // Сохраняем ключи и хосты в зашифрованные SharedPreferences (ФТ-1.1)
        securePreferences.tailscaleHost = currentState.host
        securePreferences.serverPort = portInt
        securePreferences.apiServerKey = currentState.token
        securePreferences.workdir = currentState.workdir.ifBlank { null }

        _state.update { it.copy(errorMessage = "Настройки успешно сохранены!") }
        
        // Перепроверяем соединение с обновленным хостом
        checkConnection()
    }

    private fun checkConnection() {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingConnection = true, errorMessage = null) }
            try {
                val response = apiService.checkHealth()
                if (response.isSuccessful) {
                    // 200 OK — сервер доступен. Теперь проверяем API-ключ на аутентифицированном эндпоинте.
                    val authResponse = apiService.getSessions()
                    if (authResponse.isSuccessful) {
                        _state.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.CONNECTED,
                                isCheckingConnection = false,
                                errorMessage = null
                            )
                        }
                    } else if (authResponse.code() == 401) {
                        _state.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.DISCONNECTED,
                                isCheckingConnection = false,
                                errorMessage = "Неверный API-ключ (HTTP 401). Проверьте ключ."
                            )
                        }
                    } else {
                        val activeIp = securePreferences.tailscaleHost ?: "?"
                        val activePort = securePreferences.serverPort
                        _state.update {
                            it.copy(
                                connectionStatus = ConnectionStatus.DISCONNECTED,
                                isCheckingConnection = false,
                                errorMessage = "Сервер ответил: HTTP ${authResponse.code()} (IP: $activeIp:$activePort)"
                            )
                        }
                    }
                } else {
                    val activeIp = securePreferences.tailscaleHost ?: "?"
                    val activePort = securePreferences.serverPort
                    _state.update {
                        it.copy(
                            connectionStatus = ConnectionStatus.DISCONNECTED,
                            isCheckingConnection = false,
                            errorMessage = "Сервер ответил: HTTP ${response.code()} (IP: $activeIp:$activePort)"
                        )
                    }
                }
            } catch (e: Exception) {
                val activeIp = securePreferences.tailscaleHost ?: "Не задан"
                val activePort = securePreferences.serverPort
                _state.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        isCheckingConnection = false,
                        errorMessage = "Не удалось подключиться: ${e.localizedMessage} (IP: $activeIp:$activePort)"
                    )
                }
            }
        }
    }
}
