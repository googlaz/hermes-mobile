package com.hermes.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hermes.app.data.local.entity.ChatSessionEntity

@Composable
fun ChatTabs(
    sessions: List<ChatSessionEntity>,
    activeSessionId: String?,
    onSessionSelected: (String) -> Unit,
    onAddSessionClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Прокручиваемый список вкладок чата (ФТ-2.1)
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                val isSelected = session.id == activeSessionId
                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                
                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onSessionSelected(session.id) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = session.title,
                            color = contentColor,
                            style = MaterialTheme.typography.titleSmall
                        )
                        // Показ назначенной LLM модели (ФТ-3.3)
                        Text(
                            text = session.model,
                            color = contentColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Кнопка быстрого добавления сессии (+)
        IconButton(
            onClick = onAddSessionClicked,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Добавить сессию",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
