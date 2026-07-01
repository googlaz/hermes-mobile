package com.hermes.app.ui.settings

data class SettingsState(
    val host: String = "",
    val port: String = "8642",
    val token: String = "",
    val isHostValid: Boolean = true,
    val isCheckingConnection: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.UNKNOWN,
    val errorMessage: String? = null
)

enum class ConnectionStatus {
    CONNECTED,      // ПК подключен (health-check OK)
    DISCONNECTED,   // ПК недоступен
    UNKNOWN         // Статус не проверялся
}

sealed interface SettingsEvent {
    data class OnHostChanged(val host: String) : SettingsEvent
    data class OnPortChanged(val port: String) : SettingsEvent
    data class OnTokenChanged(val token: String) : SettingsEvent
    object OnSaveClicked : SettingsEvent
    object OnCheckConnectionTriggered : SettingsEvent
}
