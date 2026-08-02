package com.antigravity.remote.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.Routes
import com.antigravity.remote.ui.components.CodeBlock
import com.antigravity.remote.ui.components.GitStatusText

@Composable
fun ProjectDashboardScreen(state: RemoteUiState, vm: RemoteViewModel, nav: NavHostController) {
    val project = state.projects.firstOrNull { it.id == state.selectedProject }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(project?.name ?: "Projeto", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate(Routes.CONVERSATIONS) }, Modifier.weight(1f)) {
                    Icon(Icons.Default.Chat, null); Text(" Conversas")
                }
                OutlinedButton(onClick = { vm.browseProjectFiles(); nav.navigate(Routes.PROJECT_FILES) }, Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, null); Text(" Arquivos")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.navigate(Routes.BUILDS) }, Modifier.weight(1f)) {
                    Icon(Icons.Default.Build, null); Text(" Builds")
                }
                OutlinedButton(onClick = vm::refreshProjectStatus, Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, null); Text(" Atualizar")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Git", fontWeight = FontWeight.Bold)
                GitStatusText(state.gitStatus)
            } }
        }
        if (state.diffStat.isNotBlank()) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Alterações", fontWeight = FontWeight.Bold)
                Text(state.diffStat, fontFamily = FontFamily.Monospace)
            } }
        }
        if (state.projectDiff.isNotBlank()) item {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar diff" else "Visualizar diff") }
            if (expanded) CodeBlock(state.projectDiff)
        }
    }
}

@Composable
fun ProjectFilesScreen(state: RemoteUiState, vm: RemoteViewModel) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = state.projectFilesCurrent.ifBlank { "/" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { state.projectFilesParent?.let(vm::browseProjectFiles) },
                        enabled = state.projectFilesParent != null,
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Voltar")
                    }
                    FilledTonalButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nova Pasta", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(state.projectFiles) { entry ->
                ListItem(
                    headlineContent = { Text(entry.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { if (entry.kind == "file") Text("${entry.size} bytes") },
                    leadingContent = {
                        Icon(
                            if (entry.kind == "folder") Icons.Default.Folder else Icons.Default.Description,
                            null,
                            tint = if (entry.kind == "folder") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable {
                        if (entry.kind == "folder") vm.browseProjectFiles(entry.path) else vm.openProjectFile(entry.path)
                    },
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        var newFolderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Criar Nova Pasta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Nome da pasta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                vm.createProjectDirectory(newFolderName.trim())
                                showCreateFolderDialog = false
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Criar Pasta", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { showCreateFolderDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ) {
                        Text("Cancelar")
                    }
                }
            },
        )
    }

    if (state.openedFilePath != null) AlertDialog(
        onDismissRequest = vm::closeProjectFile,
        title = { Text(state.openedFilePath) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(max = 520.dp).horizontalScroll(rememberScrollState())) {
                Text(state.openedFileContent.orEmpty(), fontFamily = FontFamily.Monospace)
            }
        },
        confirmButton = { TextButton(onClick = vm::closeProjectFile) { Text("Fechar") } },
    )
}
