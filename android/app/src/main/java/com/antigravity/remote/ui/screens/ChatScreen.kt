package com.antigravity.remote.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.antigravity.remote.ui.ChatLine
import com.antigravity.remote.ui.RemoteUiState
import com.antigravity.remote.ui.RemoteViewModel
import com.antigravity.remote.ui.components.GlassPanel
import com.antigravity.remote.ui.components.HeroTag
import com.antigravity.remote.ui.components.NeonChatBubble
import com.antigravity.remote.ui.components.SectionHeading
import com.antigravity.remote.ui.theme.*
import kotlinx.coroutines.delay

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ChatScreen(
    state: RemoteUiState,
    vm: RemoteViewModel,
    onOpenSubscription: () -> Unit = {},
) {
    val prompt = state.draftText
    val listState = rememberLazyListState()
    val activeTurn = state.activeTurn?.takeIf { it.conversationId == state.conversationId }
    val freeLimitReached = !state.isPro && state.dailyMessageCount >= state.dailyMessageLimit
    val selectedProjectName = state.projects
        .firstOrNull { it.id == state.selectedProject }
        ?.name
        ?: "Projeto não selecionado"
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var executionModeMenuExpanded by remember { mutableStateOf(false) }
    var progressClock by remember(activeTurn?.conversationId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(activeTurn?.conversationId) {
        while (activeTurn != null) {
            progressClock = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val secondsSinceSignal = activeTurn?.let {
        ((progressClock - it.lastSignalAt).coerceAtLeast(0) / 1_000).toInt()
    } ?: 0
    val displayedElapsed = activeTurn?.let {
        ((progressClock - it.startedAt).coerceAtLeast(0) / 1_000).toInt()
    } ?: 0
    val progressSignalDelayed = activeTurn != null && secondsSinceSignal >= 12
    val pulse = rememberInfiniteTransition(label = "waiting-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = .92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1_050), RepeatMode.Reverse),
        label = "waiting-scale",
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = .55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_050), RepeatMode.Reverse),
        label = "waiting-alpha",
    )
    val visibleItems = state.chat.size +
        (if (activeTurn != null) 1 else 0) +
        (if (state.technicalDetails.isNotEmpty()) 1 else 0) +
        (if (state.showTechnicalDetails) state.technicalDetails.size else 0)
    LaunchedEffect(visibleItems, state.chat.lastOrNull()?.text) {
        if (visibleItems > 0) {
            if (activeTurn != null) listState.scrollToItem(visibleItems - 1)
            else listState.animateScrollToItem(visibleItems - 1)
        }
    }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val compactLayout = maxWidth < 400.dp
            var controlsCollapsed by rememberSaveable { mutableStateOf(true) }
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = NeonBlue,
                padding = PaddingValues(horizontal = 14.dp, vertical = if (controlsCollapsed) 8.dp else 14.dp),
            ) {
                SectionHeading(
                    eyebrow = "Workspace ativo",
                    title = selectedProjectName,
                    subtitle = if (compactLayout || controlsCollapsed) null else "Ajuste modelo e modo antes de enviar a próxima instrução.",
                    action = {
                        FilledTonalIconButton(
                            onClick = { controlsCollapsed = !controlsCollapsed },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                if (controlsCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                contentDescription = if (controlsCollapsed) "Expandir controles" else "Recolher controles",
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeroTag(modelDisplayName(state.selectedModel))
                    HeroTag(
                        executionModeDisplayName(state.executionMode),
                        accent = if (state.executionMode == "autonomous_project") StatusError else BrandGold,
                        warm = state.executionMode == "autonomous_project",
                    )
                    if (!state.isPro) {
                        HeroTag("Free ${state.dailyMessageCount}/${state.dailyMessageLimit}", accent = NeonCyan)
                    }
                    if (activeTurn != null) {
                        HeroTag("tempo ${formatElapsed(displayedElapsed)}", accent = BrandGold, warm = true)
                    }
                }
                AnimatedVisibility(
                    visible = !controlsCollapsed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (compactLayout) {
                            SelectorButton(
                                text = modelDisplayName(state.selectedModel),
                                icon = Icons.Default.SmartToy,
                                onClick = { modelMenuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = modelMenuExpanded,
                                onDismissRequest = { modelMenuExpanded = false },
                            ) {
                                state.availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                modelDisplayName(model),
                                                fontWeight = if (model == state.selectedModel) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = {
                                            if (model == state.selectedModel) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            vm.setModel(model)
                                            modelMenuExpanded = false
                                        },
                                    )
                                }
                            }
                            SelectorButton(
                                text = executionModeDisplayName(state.executionMode),
                                icon = Icons.Default.Tune,
                                onClick = { executionModeMenuExpanded = true },
                            )
                            DropdownMenu(
                                expanded = executionModeMenuExpanded,
                                onDismissRequest = { executionModeMenuExpanded = false },
                            ) {
                                executionModes.forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                fontWeight = if (mode == state.executionMode) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        leadingIcon = {
                                            if (mode == state.executionMode) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            vm.setExecutionMode(mode)
                                            executionModeMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.weight(1f)) {
                                    SelectorButton(
                                        text = "Modelo: ${modelDisplayName(state.selectedModel)}",
                                        icon = Icons.Default.SmartToy,
                                        onClick = { modelMenuExpanded = true },
                                    )
                                    DropdownMenu(
                                        expanded = modelMenuExpanded,
                                        onDismissRequest = { modelMenuExpanded = false },
                                    ) {
                                        state.availableModels.forEach { model ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        modelDisplayName(model),
                                                        fontWeight = if (model == state.selectedModel) FontWeight.Bold else FontWeight.Normal,
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (model == state.selectedModel) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                },
                                                onClick = {
                                                    vm.setModel(model)
                                                    modelMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                Box(Modifier.weight(1f)) {
                                    SelectorButton(
                                        text = "Modo: ${executionModeDisplayName(state.executionMode)}",
                                        icon = Icons.Default.Tune,
                                        onClick = { executionModeMenuExpanded = true },
                                    )
                                    DropdownMenu(
                                        expanded = executionModeMenuExpanded,
                                        onDismissRequest = { executionModeMenuExpanded = false },
                                    ) {
                                        executionModes.forEach { (mode, label) ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        label,
                                                        fontWeight = if (mode == state.executionMode) FontWeight.Bold else FontWeight.Normal,
                                                    )
                                                },
                                                leadingIcon = {
                                                    if (mode == state.executionMode) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                },
                                                onClick = {
                                                    vm.setExecutionMode(mode)
                                                    executionModeMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Surface(
                            color = if (state.executionMode == "autonomous_project") {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                if (state.executionMode == "autonomous_project") {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                        ) {
                            Text(
                                executionModeDescription(state.executionMode),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.executionMode == "autonomous_project") {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                if (activeTurn != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroTag(turnProgressLabel(activeTurn.state))
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(state.chat) { line ->
                NeonChatBubble(line)
            }
            if (activeTurn != null) {
                item {
                    GlassPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            },
                        accent = if (progressSignalDelayed) StatusError else NeonCyan,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = if (progressSignalDelayed) StatusError else NeonCyan,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    turnProgressLabel(activeTurn.state),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    "Decorrido ${formatElapsed(displayedElapsed)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (progressSignalDelayed) {
                                    Text(
                                        "Aguardando sinal da bridge (${secondsSinceSignal}s sem atualização)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StatusError,
                                    )
                                }
                            }
                            OutlinedButton(onClick = { vm.cancel() }) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
            if (state.technicalDetails.isNotEmpty()) {
                item {
                    TextButton(onClick = vm::toggleTechnicalDetails) {
                        Icon(
                            if (state.showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.showTechnicalDetails) "Ocultar detalhes técnicos" else "Ver detalhes técnicos (${state.technicalDetails.size})")
                    }
                }
                if (state.showTechnicalDetails) {
                    items(state.technicalDetails) { line ->
                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            accent = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                line.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (!state.isPro && freeLimitReached) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Limite diário (10/10 msgs) atingido.",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onOpenSubscription) {
                        Text("Assinar Pro", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (state.chat.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple("Analisar projeto", "Analise o projeto e indique os principais problemas e próximos passos.", Icons.Default.Analytics),
                    Triple("Corrigir testes", "Execute os testes, investigue as falhas e aplique as correções necessárias.", Icons.Default.BugReport),
                    Triple("Revisar alterações", "Revise as alterações atuais do projeto e destaque riscos ou regressões.", Icons.Default.RateReview),
                ).forEach { (label, instruction, icon) ->
                    AssistChip(
                        onClick = { vm.updateDraft(instruction) },
                        enabled = !freeLimitReached,
                        label = { Text(label) },
                        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        Row(
            Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .3f), RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                prompt,
                vm::updateDraft,
                Modifier.weight(1f),
                enabled = !freeLimitReached,
                placeholder = {
                    Text(
                        when {
                            freeLimitReached -> "Limite atingido (10/10). Assine o Pro!"
                            activeTurn == null -> "Envie uma instrução…"
                            else -> "Prepare a próxima instrução…"
                        }
                    )
                },
                minLines = 1,
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    focusedLabelColor = NeonCyan,
                    cursorColor = NeonCyan,
                    unfocusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
            )
            if (activeTurn != null) {
                FilledTonalIconButton(onClick = { vm.cancel() }) { Icon(Icons.Default.Stop, "Cancelar") }
            }
            IconButton(
                enabled = activeTurn == null && !freeLimitReached && prompt.isNotBlank(),
                onClick = { if (prompt.isNotBlank()) vm.sendPrompt(prompt) },
            ) {
                Icon(
                    Icons.Default.Send,
                    "Enviar",
                    tint = if (activeTurn == null && !freeLimitReached && prompt.isNotBlank()) NeonCyan else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private val executionModes = listOf(
    "read_only" to "Somente leitura",
    "plan" to "Planejamento",
    "autonomous_project" to "Autônomo no projeto",
)

private fun executionModeDisplayName(mode: String): String =
    executionModes.firstOrNull { it.first == mode }?.second ?: mode

private fun executionModeDescription(mode: String): String = when (mode) {
    "read_only" -> "O agente analisa o projeto sem fazer alterações."
    "plan" -> "O agente entrega um plano sem implementar as alterações."
    "autonomous_project" -> "Executa sem confirmações. Use somente em projetos confiáveis."
    else -> "Escolha como o agente deve trabalhar neste projeto."
}

private fun modelDisplayName(model: String): String = model
    .replace("gemini-", "Gemini ")
    .replace("claude-", "Claude ")
    .replace("gpt-", "GPT ")
    .replace("-", " ")
    .split(" ")
    .joinToString(" ") { part ->
        part.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase() else character.toString()
        }
    }

private fun turnProgressLabel(state: String): String = when (state) {
    "sending" -> "Enviando ao computador…"
    "starting" -> "Iniciando o Antigravity…"
    "cancelling" -> "Cancelando a tarefa…"
    else -> "Antigravity está trabalhando…"
}

internal fun formatElapsed(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun SelectorButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
    }
}
