package com.antigravity.remote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.components.ListPage

@Composable
fun ApprovalsScreen(state: RemoteUiState, vm: RemoteViewModel) = ListPage("Aprovações") {
    items(state.approvals) { approval ->
        Card(Modifier.fillMaxWidth().padding(8.dp)) { Column(Modifier.padding(16.dp)) {
            Text(approval.description, fontWeight = FontWeight.Bold)
            approval.command?.let { Text("Comando: $it", fontFamily = FontFamily.Monospace) }
            approval.path?.let { Text("Local: $it") }
            approval.risk?.let { Text("Risco: $it", color = MaterialTheme.colorScheme.error) }
            approval.expiresAt?.let { Text("A aprovação expira automaticamente.", style = MaterialTheme.typography.labelSmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.approve(approval, false) }) { Text("Negar") }
                Button(onClick = { vm.approve(approval, true) }) { Text("Aprovar") }
            }
        } }
    }
}
