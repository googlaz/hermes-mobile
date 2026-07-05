package com.hermes.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hermes.app.ui.chat.ChatScreen
import com.hermes.app.ui.chat.ChatViewModel
import com.hermes.app.ui.files.FileManagerScreen
import com.hermes.app.ui.files.FileViewModel
import com.hermes.app.ui.logs.LogViewerScreen
import com.hermes.app.ui.logs.LogViewModel
import com.hermes.app.ui.models.ModelSwitcherScreen
import com.hermes.app.ui.models.ModelViewModel
import com.hermes.app.ui.settings.SettingsScreen
import com.hermes.app.ui.settings.SettingsViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Chat : Screen("chat", "Чат", Icons.Default.Send)
    object Files : Screen("files", "Файлы", Icons.Default.List)
    object Models : Screen("models", "Модели", Icons.Default.Build)
    object Logs : Screen("logs", "Логи", Icons.Default.Info)
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Пул ViewModels под Hilt DI
    val chatViewModel: ChatViewModel = hiltViewModel()
    val fileViewModel: FileViewModel = hiltViewModel()
    val modelViewModel: ModelViewModel = hiltViewModel()
    val logViewModel: LogViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    val navigationItems = listOf(
        Screen.Chat,
        Screen.Files,
        Screen.Models,
        Screen.Logs,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                navigationItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Chat.route) {
                ChatScreen(viewModel = chatViewModel)
            }
            composable(Screen.Files.route) {
                // Прокидываем активную чат-сессию в файловый менеджер (ФТ-4.3)
                val chatState by chatViewModel.uiState.collectAsState()
                androidx.compose.runtime.LaunchedEffect(chatState.activeSessionId) {
                    chatState.activeSessionId?.let { fileViewModel.setSession(it) }
                }
                FileManagerScreen(viewModel = fileViewModel)
            }
            composable(Screen.Models.route) {
                // Извлекаем состояние выбранного чата из ChatViewModel, чтобы переключать модель именно на ней (ФТ-3.2)
                val chatState by chatViewModel.uiState.collectAsState()
                val currentSession = chatState.sessions.find { it.id == chatState.activeSessionId }
                
                ModelSwitcherScreen(
                    viewModel = modelViewModel,
                    activeSessionId = chatState.activeSessionId,
                    currentSessionModel = currentSession?.model
                )
            }
            composable(Screen.Logs.route) {
                LogViewerScreen(viewModel = logViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
