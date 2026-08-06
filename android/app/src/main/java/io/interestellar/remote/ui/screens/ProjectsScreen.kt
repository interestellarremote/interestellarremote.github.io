package io.interestellar.remote.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.navigation.NavHostController
import io.interestellar.remote.ui.RemoteUiState
import io.interestellar.remote.ui.RemoteViewModel
import io.interestellar.remote.ui.Routes
import io.interestellar.remote.ui.components.GlassPanel
import io.interestellar.remote.ui.components.HeroTag
import io.interestellar.remote.ui.components.MetricPill
import io.interestellar.remote.ui.components.SectionHeading
import io.interestellar.remote.ui.theme.*

@Composable
fun ProjectsScreen(state: RemoteUiState, vm: RemoteViewModel, nav: NavHostController) {
    val successCount = state.projects.count { state.projectBuildStatuses[it.id] == io.interestellar.remote.ui.BuildVisualStatus.SUCCESS }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), accent = NeonBlue) {
                SectionHeading(
                    eyebrow = "Workspace",
                    title = "Projetos autorizados",
                    subtitle = "Selecione um projeto para abrir dashboard, arquivos, builds e conversas com o agente.",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricPill(value = "${state.projects.size}", label = "autorizados", accent = BrandTeal)
                    MetricPill(value = "$successCount", label = "com build ok", accent = StatusActive)
                    MetricPill(
                        value = if (state.fullFilesystemAccess) "TOTAL" else "LIMITADO",
                        label = "acesso a disco",
                        accent = if (state.fullFilesystemAccess) StatusError else BrandGold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = vm::openDirectoryBrowser,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Criar / Adicionar Pasta", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = vm::refreshProjects,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sincronizar")
                    }
                }
            }
        }
        items(state.projects.size) { index ->
            val project = state.projects[index]
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    vm.selectProject(project.id)
                    nav.navigate(Routes.PROJECT_DASHBOARD)
                },
                colors = CardDefaults.cardColors(containerColor = DeviceCardBackground),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, projectBuildStatusColor(state.projectBuildStatuses[project.id]).copy(alpha = 0.32f)),
                        modifier = Modifier.size(50.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = projectBuildStatusColor(state.projectBuildStatuses[project.id]))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${project.buildProfiles.size} perfil(is) de build",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HeroTag(
                                text = when (state.projectBuildStatuses[project.id]) {
                                    io.interestellar.remote.ui.BuildVisualStatus.SUCCESS -> "build saudável"
                                    io.interestellar.remote.ui.BuildVisualStatus.ERROR -> "atenção"
                                    null -> "sem status"
                                },
                                accent = projectBuildStatusColor(state.projectBuildStatuses[project.id]),
                                warm = state.projectBuildStatuses[project.id] == io.interestellar.remote.ui.BuildVisualStatus.ERROR,
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ArrowOutward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.projects.isEmpty()) item {
            GlassPanel(modifier = Modifier.fillMaxWidth(), accent = BrandGold) {
                Text(
                    if (state.projectsLoaded) "Nenhum projeto autorizado ainda"
                    else "Sincronizando projetos autorizados…",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.projectsLoaded) {
                        "Use o botão de pasta para adicionar uma raiz autorizada do computador e começar a operar o agente por projeto."
                    } else {
                        "O app está aguardando a lista enviada pelo computador conectado."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (state.directoryBrowserOpen) DirectoryBrowserDialog(state, vm)
}

@Composable
fun DirectoryBrowserDialog(state: RemoteUiState, vm: RemoteViewModel) {
    val suggestedName = state.directoryCurrent
        ?.trimEnd('\\', '/')
        ?.substringAfterLast('\\')
        ?.substringAfterLast('/')
        .orEmpty()
    var projectName by androidx.compose.runtime.remember(state.directoryCurrent) { androidx.compose.runtime.mutableStateOf(suggestedName) }
    var showCreateFolderDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = vm::closeDirectoryBrowser,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Navegador de Pastas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (state.fullFilesystemAccess) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = if (state.fullFilesystemAccess) "Acesso Total" else "Raízes Autorizadas",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (state.fullFilesystemAccess) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = state.directoryCurrent ?: "Selecione uma pasta autorizada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.error != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                // Toolbar buttons evenly distributed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { vm.browseDirectory(state.directoryParent) },
                        enabled = state.directoryParent != null,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Voltar", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { vm.browseDirectory(null) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Raízes", style = MaterialTheme.typography.labelMedium)
                    }

                    FilledTonalButton(
                        onClick = { showCreateFolderDialog = true },
                        enabled = state.directoryCurrent != null,
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Nova Pasta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                // Folder list container
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                ) {
                    LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                        items(state.directoryEntries) { entry ->
                            ListItem(
                                headlineContent = { Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = NeonBlue
                                    )
                                },
                                modifier = Modifier.clickable { vm.browseDirectory(entry.path) },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                            )
                        }
                        if (state.directoryEntries.isEmpty()) item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Nenhuma pasta encontrada neste diretório.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Project name input
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nome do projeto no app") },
                    singleLine = true,
                    enabled = state.directoryCurrent != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { vm.addProjectFromDirectory(projectName) },
                    enabled = state.directoryCurrent != null && projectName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Usar esta pasta como Projeto", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = vm::closeDirectoryBrowser,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Cancelar")
                }
            }
        },
    )

    if (showCreateFolderDialog) {
        var newFolderName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var customProjectName by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        var useAsProjectOption by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = NeonBlue)
                    Text("Criar Nova Pasta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (state.error != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = state.error,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = {
                            newFolderName = it
                            if (customProjectName.isBlank()) customProjectName = it
                        },
                        label = { Text("Nome da pasta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().clickable { useAsProjectOption = !useAsProjectOption },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Checkbox(
                                checked = useAsProjectOption,
                                onCheckedChange = { useAsProjectOption = it },
                            )
                            Column {
                                Text("Usar como projeto do app", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Cria a pasta, navega até ela e seleciona como projeto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (useAsProjectOption) {
                        OutlinedTextField(
                            value = customProjectName,
                            onValueChange = { customProjectName = it },
                            label = { Text("Nome do projeto no app") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                vm.createDirectory(
                                    name = newFolderName.trim(),
                                    navigate = false,
                                    useAsProject = useAsProjectOption,
                                    projectName = customProjectName.ifBlank { newFolderName.trim() },
                                )
                                showCreateFolderDialog = false
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (useAsProjectOption) "Criar e Usar como Projeto" else "Criar Pasta",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    OutlinedButton(
                        onClick = { showCreateFolderDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text("Cancelar")
                    }
                }
            },
        )
    }
}

private fun projectBuildStatusColor(status: io.interestellar.remote.ui.BuildVisualStatus?): androidx.compose.ui.graphics.Color = when (status) {
    io.interestellar.remote.ui.BuildVisualStatus.SUCCESS -> StatusActive
    io.interestellar.remote.ui.BuildVisualStatus.ERROR -> StatusError
    null -> StatusIdle
}

