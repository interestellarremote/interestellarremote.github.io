package io.interestellar.remote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.interestellar.remote.data.TaskEntity
import io.interestellar.remote.ui.RemoteUiState
import io.interestellar.remote.ui.RemoteViewModel
import io.interestellar.remote.ui.Routes
import io.interestellar.remote.ui.components.ListPage

@Composable
fun InboxScreen(state: RemoteUiState, vm: RemoteViewModel, nav: NavHostController) {
    ListPage("Caixa de entrada") {
        if (state.tasks.isEmpty()) item { Text("Nenhuma tarefa registrada.", Modifier.padding(16.dp)) }
        items(state.tasks) { task ->
            TaskCard(task, onOpen = {
                vm.openTask(task)
                nav.navigate(Routes.CHAT)
            }, onRetry = { vm.retryTask(task) }, showRetry = task.status in setOf("ERROR", "CANCELLED"))
        }
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onOpen: () -> Unit, onRetry: () -> Unit, showRetry: Boolean) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = if (task.unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(taskStatusLabel(task.status), fontWeight = FontWeight.Bold)
                Text(formatElapsed(task.elapsedSeconds), style = MaterialTheme.typography.labelSmall)
            }
            Text(task.prompt.ifBlank { "Tarefa recuperada do computador" }, maxLines = 3)
            if (!task.error.isNullOrBlank()) Text(task.error, color = MaterialTheme.colorScheme.error)
            if (showRetry && task.prompt.isNotBlank()) OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null)
                Text(" Tentar novamente")
            }
        }
    }
}

private fun taskStatusLabel(status: String): String = when (status) {
    "QUEUED" -> "Aguardando computador"
    "STARTING" -> "Iniciando"
    "RUNNING" -> "Em execução"
    "COMPLETE" -> "Concluída"
    "CANCELLED" -> "Cancelada"
    "ERROR" -> "Erro"
    else -> status
}

