package com.antigravity.remote.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.data.BuildSummary

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun BuildsScreen(state: RemoteUiState, vm: RemoteViewModel) {
    val project = state.projects.firstOrNull { it.id == state.selectedProject }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Builds", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            project?.buildProfiles?.forEach { profile: BuildSummary -> Button(onClick = { vm.build(profile.id) }) { Text(profile.name) } }
            if (state.activeBuildId != null) OutlinedButton(onClick = vm::cancelBuild) { Icon(Icons.Default.Stop, null); Text(" Cancelar") }
        }
        Text(state.buildLog.ifEmpty { "Nenhum build executado." }, modifier = Modifier.weight(1f))
    }
}
