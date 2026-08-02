package com.antigravity.remote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antigravity.remote.ui.ArtifactUi
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.components.ListPage
import com.antigravity.remote.ui.components.cardClick

@Composable
fun ArtifactsScreen(state: RemoteUiState, vm: RemoteViewModel) {
    var pending by remember { mutableStateOf<ArtifactUi?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val artifact = pending
        if (uri != null && artifact != null) vm.downloadArtifact(artifact, uri)
        pending = null
    }
    ListPage("Artefatos") {
        items(state.artifacts) { artifact ->
            ListItem(
                headlineContent = { Text(artifact.displayName) },
                supportingContent = { Text("${artifact.size} bytes · cifrado ponta a ponta") },
                leadingContent = { Icon(Icons.Default.Download, null) },
                modifier = Modifier.cardClick { pending = artifact; save.launch(artifact.displayName) }
            )
        }
        if (state.artifacts.isEmpty()) item { Text("Nenhum artefato recebido.", Modifier.padding(16.dp)) }
    }
}
