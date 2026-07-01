package com.hermes.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    // Безопасный сбор стейта без Recomposition loops (Pitfall 5)
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Настройки подключения",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // --- Баннер статуса подключения (ФТ-1.2, ФТ-3.8 / КП-5) ---
        ConnectionStatusBanner(status = state.connectionStatus, isChecking = state.isCheckingConnection)

        Spacer(modifier = Modifier.height(24.dp))

        // Поле ввода Tailscale хоста
        OutlinedTextField(
            value = state.host,
            onValueChange = { viewModel.onEvent(SettingsEvent.OnHostChanged(it)) },
            label = { Text("Tailscale IP или MagicDNS хост ПК") },
            placeholder = { Text("например, 100.115.92.14") },
            isError = !state.isHostValid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        if (!state.isHostValid) {
            Text(
                text = "Неверный диапазон Tailscale IP",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода Порта
        OutlinedTextField(
            value = state.port,
            onValueChange = { viewModel.onEvent(SettingsEvent.OnPortChanged(it)) },
            label = { Text("Порт Hermes API Server") },
            placeholder = { Text("8642") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Поле ввода Token (API_SERVER_KEY)
        OutlinedTextField(
            value = state.token,
            onValueChange = { viewModel.onEvent(SettingsEvent.OnTokenChanged(it)) },
            label = { Text("API Ключ (API_SERVER_KEY)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Сигнальное сообщение (ошибки, успехи)
        state.errorMessage?.let { message ->
            Text(
                text = message,
                color = if (message.contains("успешно")) Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Кнопка сохранения настроек
        Button(
            onClick = { viewModel.onEvent(SettingsEvent.OnSaveClicked) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Сохранить настройки", color = MaterialTheme.colorScheme.onPrimary)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка ручной перепроверки сокета
        OutlinedButton(
            onClick = { viewModel.onEvent(SettingsEvent.OnCheckConnectionTriggered) },
            enabled = !state.isCheckingConnection,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (state.isCheckingConnection) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("Проверить соединение")
            }
        }
    }
}

@Composable
fun ConnectionStatusBanner(
    status: ConnectionStatus,
    isChecking: Boolean
) {
    val backgroundColor: Color
    val contentColor: Color
    val text: String
    val icon: @Composable () -> Unit

    when (status) {
        ConnectionStatus.CONNECTED -> {
            backgroundColor = Color(0xFF1B5E20) // Темно-зеленый фон
            contentColor = Color(0xFFE8F5E9)
            text = "Подключено к Hermes"
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = "OK", tint = Color(0xFF81C784)) }
        }
        ConnectionStatus.DISCONNECTED -> {
            backgroundColor = Color(0xFFB71C1C) // Ярко-красный аварийный баннер (КП-5)
            contentColor = Color(0xFFFFEBEE)
            text = "ПК недоступен"
            icon = { Icon(Icons.Default.Warning, contentDescription = "Error", tint = Color(0xFFEF5350)) }
        }
        ConnectionStatus.UNKNOWN -> {
            backgroundColor = Color(0xFF333333)
            contentColor = Color(0xFFCCCCCC)
            text = "Статус подключения неизвестен"
            icon = { Icon(Icons.Default.Info, contentDescription = "Checking", tint = Color.Gray) }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, shape = RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Проверка связи...", color = contentColor, style = MaterialTheme.typography.bodyLarge)
        } else {
            icon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, color = contentColor, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
