package com.antigravity.remote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.Routes
import com.antigravity.remote.ui.components.ListPage
import com.antigravity.remote.ui.components.cardClick
import com.antigravity.remote.ui.theme.*

@Composable
fun ConversationsScreen(state: RemoteUiState, vm: RemoteViewModel, nav: NavHostController) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    var editing by remember { mutableStateOf<com.antigravity.remote.data.ConversationEntity?>(null) }
    var deleting by remember { mutableStateOf<com.antigravity.remote.data.ConversationEntity?>(null) }
    var title by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { vm.newConversation(); nav.navigate(Routes.CHAT) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text(" Nova conversa") }
        androidx.compose.foundation.lazy.LazyColumn {
            items(state.conversations) { conversation ->
                val latestTask = state.tasks.firstOrNull { it.conversationId == conversation.id }
                ListItem(
                    headlineContent = { Text(conversation.title) },
                    supportingContent = { Text(conversationPreview(latestTask?.prompt ?: conversation.title)) },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(conversationStatusColor(latestTask?.status), RoundedCornerShape(50)))
                            Icon(Icons.Default.Chat, null)
                        }
                    },
                    trailingContent = { Row {
                        IconButton(onClick = { editing = conversation; title = conversation.title }) { Icon(Icons.Default.Edit, "Renomear") }
                        IconButton(onClick = { vm.archiveConversation(conversation.id) }) { Icon(Icons.Outlined.Archive, "Arquivar") }
                        IconButton(onClick = { deleting = conversation }) {
                            Icon(Icons.Default.DeleteOutline, "Excluir", tint = MaterialTheme.colorScheme.error)
                        }
                    } },
                    modifier = Modifier.cardClick { vm.selectConversation(conversation.id); nav.navigate(Routes.CHAT) }
                )
            }
        }
    }
    if (editing != null) AlertDialog(
        onDismissRequest = { editing = null },
        title = { Text("Renomear conversa") },
        text = { OutlinedTextField(title, { title = it }) },
        confirmButton = { TextButton(onClick = { vm.renameConversation(editing!!.id, title); editing = null }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
    )
    if (deleting != null) AlertDialog(
        onDismissRequest = { deleting = null },
        title = { Text("Excluir conversa?") },
        text = { Text("Esta ação remove a conversa da lista neste dispositivo.") },
        confirmButton = {
            TextButton(onClick = { vm.deleteConversation(deleting!!.id); deleting = null }) {
                Text("Excluir", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
    )
}

private fun conversationPreview(text: String): String {
    val singleLine = text.replace(Regex("\\s+"), " ").trim()
    return when {
        singleLine.isBlank() -> "Sem mensagens ainda"
        singleLine.length <= 40 -> singleLine
        else -> singleLine.take(37).trimEnd() + "..."
    }
}

private fun conversationStatusColor(status: String?): androidx.compose.ui.graphics.Color = when (status) {
    "QUEUED", "STARTING", "RUNNING", "CANCELLING" -> StatusActive
    "ERROR", "CANCELLED" -> StatusError
    else -> StatusIdle
}
