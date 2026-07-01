package com.hermes.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Строго темная палитра цветов (НФТ-3, без светлой темы)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),       // Светло-синий акцент
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1976D2),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF81C784),     // Дополнительный зеленый (для статусов)
    onSecondary = Color(0xFF1B5E20),
    background = Color(0xFF121212),    // Чистый темно-серый фон
    onBackground = Color(0xFFEEEEEE),
    surface = Color(0xFF1E1E1E),       // Панели и карточки
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFB0B0B0),
    error = Color(0xFFEF5350),         // Красный для ошибок и недоступности ПК
    onError = Color(0xFFD32F2F)
)

@Composable
fun HermesAppTheme(
    content: @Composable () -> Unit
) {
    // Согласно ТЗ (ФТ-6.2, НФТ-3) приложение работает ТОЛЬКО в темной теме
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
