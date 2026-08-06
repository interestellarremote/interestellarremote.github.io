package io.interestellar.remote.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.interestellar.remote.ui.RemoteUiState
import io.interestellar.remote.ui.components.ListPage

@Composable
fun AuditScreen(state: RemoteUiState) = ListPage("Auditoria") {
    if (state.auditLog.isEmpty()) item { Text("Nenhum evento registrado.", Modifier.padding(16.dp)) }
    items(state.auditLog) { entry ->
        ListItem(
            headlineContent = { Text(entry.description) },
            supportingContent = { Text("${entry.kind} · ${entry.taskId?.take(8) ?: "sistema"}") },
            leadingContent = { Icon(Icons.Default.VerifiedUser, null) },
        )
    }
}

