package com.antigravity.remote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.antigravity.remote.data.*
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val SAVED_DEVICE = "selected_device"
private const val SAVED_PROJECT = "selected_project"
private const val SAVED_CONVERSATION = "selected_conversation"
private const val SAVED_MODEL = "selected_model"

val DefaultAvailableModels = listOf(
    "gemini-3.6-flash-high",
    "gemini-3.6-flash-medium",
    "gemini-3.6-flash-low",
    "gemini-3.5-flash-high",
    "gemini-3.5-flash-medium",
    "gemini-3.5-flash-low",
    "gemini-3.1-pro-high",
    "gemini-3.1-pro-low",
    "claude-sonnet-4-6",
    "claude-opus-4-6-thinking",
    "gpt-oss-120b-medium",
)

data class ChatLine(val role: String, val text: String, val kind: String = "text")
data class TurnProgressUi(
    val taskId: String,
    val conversationId: String,
    val state: String,
    val elapsedSeconds: Int,
    val startedAt: Long = System.currentTimeMillis(),
    val lastSignalAt: Long = System.currentTimeMillis(),
    val lastSequence: Long = 0,
)
data class ApprovalUi(
    val id: String,
    val conversationId: String,
    val description: String,
    val command: String? = null,
    val path: String? = null,
    val risk: String? = null,
    val expiresAt: Long? = null,
)
data class ArtifactUi(val remoteName: String, val displayName: String, val conversationId: String, val size: Long, val keyVersion: Int)
data class DirectoryEntryUi(val name: String, val path: String)
data class ProjectFileUi(val name: String, val path: String, val kind: String, val size: Long)
enum class BuildVisualStatus { SUCCESS, ERROR }
data class RemoteUiState(
    val signedIn: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val devices: List<DeviceSummary> = emptyList(),
    val selectedDevice: String? = null,
    val projects: List<RemoteProject> = emptyList(),
    val projectsLoaded: Boolean = false,
    val selectedProject: String? = null,
    val conversationId: String = UUID.randomUUID().toString(),
    val chat: List<ChatLine> = emptyList(),
    val technicalDetails: List<ChatLine> = emptyList(),
    val showTechnicalDetails: Boolean = false,
    val activeTurn: TurnProgressUi? = null,
    val approvals: List<ApprovalUi> = emptyList(),
    val buildLog: String = "",
    val artifacts: List<ArtifactUi> = emptyList(),
    val conversations: List<ConversationEntity> = emptyList(),
    val activeBuildId: String? = null,
    val directoryBrowserOpen: Boolean = false,
    val directoryCurrent: String? = null,
    val directoryParent: String? = null,
    val directoryEntries: List<DirectoryEntryUi> = emptyList(),
    val fullFilesystemAccess: Boolean = false,
    val tasks: List<TaskEntity> = emptyList(),
    val bridgeVersion: String? = null,
    val protocolVersion: Int = 1,
    val selectedModel: String = "gemini-3.6-flash-medium",
    val availableModels: List<String> = DefaultAvailableModels,
    val executionMode: String = "autonomous_project",
    val draftText: String = "",
    val projectFiles: List<ProjectFileUi> = emptyList(),
    val projectFilesCurrent: String = "",
    val projectFilesParent: String? = null,
    val openedFilePath: String? = null,
    val openedFileContent: String? = null,
    val gitStatus: String = "",
    val diffStat: String = "",
    val projectDiff: String = "",
    val projectBuildStatuses: Map<String, BuildVisualStatus> = emptyMap(),
    val auditLog: List<AuditEntity> = emptyList(),
    val isOffline: Boolean = false,
    val isPro: Boolean = false,
    val dailyMessageCount: Int = 0,
    val dailyMessageLimit: Int = 10,
    val devForcePro: Boolean = false,
)

@HiltViewModel
class RemoteViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val repository: RemoteRepository,
    private val dao: RemoteDao,
    private val savedState: SavedStateHandle,
    private val sessionStore: SessionStore,
    private val networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val restoredDevice = sessionStore.deviceId ?: savedState.get<String>(SAVED_DEVICE)
    private val restoredProject = sessionStore.projectId ?: savedState.get<String>(SAVED_PROJECT)
    private val restoredConversation = sessionStore.conversationId ?: savedState.get<String>(SAVED_CONVERSATION)
    private val restoredModel = sessionStore.model ?: savedState.get<String>(SAVED_MODEL) ?: "gemini-3.6-flash-medium"
    private val _state = MutableStateFlow(
        RemoteUiState(
            signedIn = auth.currentUser != null,
            selectedDevice = restoredDevice,
            selectedProject = restoredProject,
            conversationId = restoredConversation ?: UUID.randomUUID().toString(),
            selectedModel = restoredModel,
        )
    )
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()
    private var deviceJob: Job? = null
    private val eventJobs = mutableMapOf<String, Job>()
    private var conversationJob: Job? = null
    private var messageJob: Job? = null
    private var pendingSelectProjectName: String? = null
    private var tasksJob: Job? = null
    private var auditJob: Job? = null
    private val projectSnapshots = mutableMapOf<String, List<RemoteProject>>()
    private val restoredHistoryDevices = mutableSetOf<String>()
    private val historyBackupJobs = mutableMapOf<String, Job>()

    init {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (sessionStore.dailyMessageDate != today) {
            sessionStore.dailyMessageDate = today
            sessionStore.dailyMessageCount = 0
        }
        _state.value = _state.value.copy(
            devForcePro = sessionStore.devForcePro,
            dailyMessageCount = sessionStore.dailyMessageCount,
            isPro = sessionStore.devForcePro,
        )
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _state.value = _state.value.copy(isOffline = !online)
            }
        }
        if (auth.currentUser != null) {
            observeTasks()
            observeAudit()
            observeDevices()
            if (restoredDevice != null && restoredProject != null) {
                observeConversations(restoredDevice, restoredProject)
            }
            if (restoredConversation != null) observeConversationMessages(restoredConversation)
            launchAction { repository.registerPushToken() }
            viewModelScope.launch {
                dao.pruneProcessedEvents(System.currentTimeMillis() - 8L * 24 * 60 * 60 * 1_000)
            }
        }
    }

    private fun observeConversations(deviceId: String, projectId: String) {
        conversationJob?.cancel()
        conversationJob = viewModelScope.launch {
            dao.conversations(deviceId, projectId).collect { values ->
                _state.value = _state.value.copy(conversations = values)
            }
        }
    }

    private fun restoreHistoryIfNeeded(deviceIds: List<String>) {
        val pendingDevices = deviceIds.filterNot(restoredHistoryDevices::contains)
        if (pendingDevices.isEmpty()) return
        restoredHistoryDevices += pendingDevices
        viewModelScope.launch {
            runCatching { repository.restoreConversationBackups(pendingDevices) }
                .onSuccess { backups ->
                    backups.forEach { backup ->
                        val local = dao.conversation(backup.conversation.id)
                        if (local == null || backup.conversation.updatedAt >= local.updatedAt) {
                            dao.saveConversation(backup.conversation)
                            backup.messages.forEach { message -> dao.saveMessage(message) }
                        }
                    }
                    pendingDevices.forEach { deviceId ->
                        dao.conversationsForDevice(deviceId).forEach { conversation ->
                            repository.backupConversation(conversation, dao.messagesOnce(conversation.id))
                        }
                    }
                }
                .onFailure { restoredHistoryDevices.removeAll(pendingDevices.toSet()) }
        }
    }

    private fun scheduleHistoryBackup(conversationId: String) {
        historyBackupJobs.remove(conversationId)?.cancel()
        historyBackupJobs[conversationId] = viewModelScope.launch {
            delay(1_500)
            val conversation = dao.conversation(conversationId) ?: return@launch
            runCatching { repository.backupConversation(conversation, dao.messagesOnce(conversationId)) }
        }
    }

    private fun observeConversationMessages(conversationId: String) {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            dao.messages(conversationId).collect { values ->
                if (_state.value.conversationId != conversationId) return@collect
                val agentTechnical = values.filter { it.role == "agent" }
                    .mapNotNull { message ->
                        presentAgentMessage(message.content).technical
                            .takeIf(String::isNotBlank)
                            ?.let { ChatLine("system", it, "AGENT_INTERNAL:${message.id}") }
                    }
                _state.value = _state.value.copy(
                    chat = values.filter { it.role != "system" }
                        .map { message ->
                            ChatLine(
                                message.role,
                                if (message.role == "agent") presentAgentMessage(message.content).visible
                                else message.content,
                            )
                        }
                        .filter { it.text.isNotBlank() },
                    technicalDetails = values.filter { it.role == "system" }
                        .map { ChatLine(it.role, it.content, it.status) } + agentTechnical,
                )
            }
        }
        viewModelScope.launch {
            val draft = dao.draft(conversationId)?.text.orEmpty()
            if (_state.value.conversationId == conversationId) {
                _state.value = _state.value.copy(draftText = draft)
            }
        }
    }

    private fun observeAudit() {
        auditJob?.cancel()
        auditJob = viewModelScope.launch {
            dao.audit().collect { values -> _state.value = _state.value.copy(auditLog = values) }
        }
    }

    private fun observeTasks() {
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
            dao.tasks().collect { tasks ->
                val state = _state.value
                val active = tasks.firstOrNull {
                    it.conversationId == state.conversationId &&
                        it.status in setOf("QUEUED", "STARTING", "RUNNING", "CANCELLING")
                }
                _state.value = state.copy(
                    tasks = tasks,
                    activeTurn = when {
                        active == null -> state.activeTurn?.takeUnless {
                            tasks.any { task -> task.id == it.taskId && task.status in setOf("COMPLETE", "ERROR", "CANCELLED") }
                        }
                        state.activeTurn?.taskId == active.id -> state.activeTurn
                        else -> TurnProgressUi(
                            active.id,
                            active.conversationId,
                            active.phase,
                            active.elapsedSeconds,
                            System.currentTimeMillis() - active.elapsedSeconds * 1_000L,
                        )
                    },
                )
            }
        }
    }

    fun onSignedIn() {
        _state.value = _state.value.copy(signedIn = true)
        observeTasks()
        observeAudit()
        observeDevices()
        launchAction { repository.registerPushToken() }
    }

    private fun observeDevices() {
        deviceJob?.cancel()
        deviceJob = viewModelScope.launch {
            repository.devices().catch { error ->
                if (auth.currentUser != null) fail(error)
            }.collect { devices ->
                if (auth.currentUser != null) {
                    _state.value = _state.value.copy(devices = devices)
                    val deviceIds = devices.map { it.id }
                    restoreHistoryIfNeeded(deviceIds)
                    observeEvents(deviceIds.toSet())
                }
            }
        }
    }

    private fun observeEvents(deviceIds: Set<String>) {
        eventJobs.filterValues { !it.isActive }.keys.toList().forEach(eventJobs::remove)
        (eventJobs.keys - deviceIds).forEach { deviceId ->
            eventJobs.remove(deviceId)?.cancel()
        }
        (deviceIds - eventJobs.keys).forEach { deviceId ->
            eventJobs[deviceId] = viewModelScope.launch {
                val acknowledgements = Channel<String>(Channel.UNLIMITED)
                val acknowledgementJob = launch {
                    processAcknowledgements(deviceId, acknowledgements)
                }
                try {
                    repository.events(deviceId).catch { error ->
                        if (auth.currentUser != null) fail(error)
                    }.collect { event ->
                        try {
                            val eventId = event.envelope.messageId
                            if (event.envelope.type == MessageType.TEXT_DELTA) {
                                handleTextDelta(event)
                            } else if (!dao.isEventProcessed(eventId)) {
                                handleEvent(event)
                                dao.saveProcessedEvent(ProcessedEventEntity(eventId, System.currentTimeMillis()))
                            }
                            acknowledgements.send(eventId)
                        } catch (error: Exception) {
                            if (auth.currentUser != null) fail(error)
                        }
                    }
                } finally {
                    acknowledgements.close()
                    acknowledgementJob.cancel()
                }
            }
        }
    }

    private suspend fun processAcknowledgements(deviceId: String, channel: Channel<String>) {
        val pending = linkedSetOf<String>()
        while (currentCoroutineContext().isActive) {
            if (pending.isEmpty()) {
                val first = channel.receiveCatching().getOrNull() ?: break
                pending += first
            }
            delay(120)
            while (pending.size < 100) {
                val next = channel.tryReceive().getOrNull() ?: break
                pending += next
            }
            runCatching { repository.acknowledgeEvents(deviceId, pending) }
                .onSuccess { pending.clear() }
                .onFailure { delay(1_000) }
        }
    }

    fun onAppForegrounded() {
        if (auth.currentUser == null) return
        eventJobs.values.forEach(Job::cancel)
        eventJobs.clear()
        observeDevices()
        _state.value.selectedDevice?.let { deviceId ->
            launchAction { repository.sync(deviceId) }
        }
    }

    fun pair(payload: PairingPayload) = launchAction {
        repository.claimPairing(payload)
    }

    fun selectDevice(deviceId: String) {
        conversationJob?.cancel()
        messageJob?.cancel()
        savedState[SAVED_DEVICE] = deviceId
        sessionStore.deviceId = deviceId
        sessionStore.projectId = null
        sessionStore.conversationId = null
        savedState.remove<String>(SAVED_PROJECT)
        savedState.remove<String>(SAVED_CONVERSATION)
        val cachedProjects = projectSnapshots[deviceId]
        _state.value = _state.value.copy(
            selectedDevice = deviceId,
            projects = cachedProjects.orEmpty(),
            projectsLoaded = cachedProjects != null,
            selectedProject = null,
            conversations = emptyList(),
            chat = emptyList(),
            technicalDetails = emptyList(),
            showTechnicalDetails = false,
        )
        launchAction { repository.sync(deviceId) }
    }

    private suspend fun handleEvent(event: DecryptedEvent) {
        when (event.envelope.type) {
            MessageType.SNAPSHOT -> {
                val projects = event.payload.optJSONArray("projects")?.let(::parseProjects).orEmpty()
                projectSnapshots[event.envelope.deviceId] = projects
                syncSnapshotTasks(event)
                if (_state.value.selectedDevice == event.envelope.deviceId) {
                    val modelsJson = event.payload.optJSONArray("availableModels")
                    val availableModels = if (modelsJson != null && modelsJson.length() > 0) {
                        (0 until modelsJson.length()).map(modelsJson::getString)
                    } else {
                        _state.value.availableModels
                    }
                    _state.value = _state.value.copy(
                        projects = projects,
                        projectsLoaded = true,
                        bridgeVersion = event.payload.optString("bridgeVersion").takeIf(String::isNotBlank),
                        protocolVersion = event.payload.optInt("protocolVersion", 1),
                        availableModels = availableModels,
                    )
                    pendingSelectProjectName?.let { targetName ->
                        val matched = projects.firstOrNull { it.name == targetName }
                        if (matched != null) {
                            pendingSelectProjectName = null
                            selectProject(matched.id)
                        }
                    }
                }
            }
            MessageType.TEXT_DELTA -> Unit
            MessageType.THOUGHT_DELTA, MessageType.TOOL_CALL -> {
                val content = technicalEventText(event)
                dao.saveMessage(
                    MessageEntity(
                        event.envelope.messageId,
                        event.envelope.conversationId,
                        "system",
                        content,
                        event.envelope.createdAt,
                        event.envelope.type.name,
                    )
                )
            }
            MessageType.BUILD_LOG ->
                _state.value = _state.value.let { current ->
                    current.copy(
                        buildLog = current.buildLog + event.payload.optString("text", event.payload.toString()),
                        activeBuildId = if (event.payload.has("buildId")) event.payload.getString("buildId") else current.activeBuildId,
                    )
                }
            MessageType.BUILD_RESULT -> {
                val succeeded = when {
                    event.payload.has("success") -> event.payload.optBoolean("success")
                    else -> event.payload.optString("status").uppercase() in setOf("SUCCESS", "SUCCEEDED", "COMPLETE", "COMPLETED")
                }
                _state.value = _state.value.let { current ->
                    val statuses = current.selectedProject?.let { projectId ->
                        current.projectBuildStatuses + (projectId to if (succeeded) BuildVisualStatus.SUCCESS else BuildVisualStatus.ERROR)
                    } ?: current.projectBuildStatuses
                    current.copy(
                        buildLog = current.buildLog + "\n${event.payload}\n",
                        activeBuildId = null,
                        projectBuildStatuses = statuses,
                    )
                }
            }
            MessageType.APPROVAL_REQUEST -> {
                val approval = ApprovalUi(
                    event.payload.getString("approvalId"),
                    event.envelope.conversationId,
                    event.payload.optString("description", "Ação sensível"),
                    event.payload.optString("command").takeIf(String::isNotBlank),
                    event.payload.optString("path").takeIf(String::isNotBlank),
                    event.payload.optString("risk").takeIf(String::isNotBlank),
                    event.payload.optLong("expiresAt").takeIf { it > 0 },
                )
                _state.value = _state.value.let { current -> current.copy(approvals = current.approvals + approval) }
            }
            MessageType.ARTIFACT -> {
                val artifact = ArtifactUi(
                    event.payload.getString("remoteName"), event.payload.optString("displayName", "artefato"),
                    event.envelope.conversationId, event.payload.optLong("size"), event.envelope.keyVersion
                )
                _state.value = _state.value.let { current -> current.copy(artifacts = current.artifacts + artifact) }
            }
            MessageType.DIRECTORY_LIST -> {
                val entries = event.payload.optJSONArray("entries") ?: org.json.JSONArray()
                _state.value = _state.value.copy(
                    directoryCurrent = event.payload.optString("current").takeIf { it.isNotEmpty() },
                    directoryParent = event.payload.optString("parent").takeIf { it.isNotEmpty() },
                    directoryEntries = (0 until entries.length()).map { index ->
                        val entry = entries.getJSONObject(index)
                        DirectoryEntryUi(entry.getString("name"), entry.getString("path"))
                    },
                    fullFilesystemAccess = event.payload.optBoolean("allowFullFilesystem", false),
                )
            }
            MessageType.PROJECT_FILE_LIST -> {
                val entries = event.payload.optJSONArray("entries") ?: org.json.JSONArray()
                _state.value = _state.value.copy(
                    projectFilesCurrent = event.payload.optString("current"),
                    projectFilesParent = event.payload.optString("parent").takeIf(String::isNotBlank),
                    projectFiles = (0 until entries.length()).map { index ->
                        val entry = entries.getJSONObject(index)
                        ProjectFileUi(
                            entry.getString("name"), entry.getString("path"),
                            entry.getString("kind"), entry.optLong("size"),
                        )
                    },
                )
            }
            MessageType.PROJECT_FILE_CONTENT -> _state.value = _state.value.copy(
                openedFilePath = event.payload.optString("path"),
                openedFileContent = event.payload.optString("content"),
            )
            MessageType.PROJECT_STATUS -> _state.value = _state.value.copy(
                gitStatus = event.payload.optString("gitStatus"),
                diffStat = event.payload.optString("diffStat"),
                projectDiff = event.payload.optString("diff"),
            )
            MessageType.HEARTBEAT -> {
                if (event.payload.optString("scope") == "turn") {
                    val taskId = eventTaskId(event)
                    val current = _state.value.activeTurn
                    if (current?.taskId != taskId ||
                        event.envelope.sequence > current.lastSequence
                    ) {
                        val incomingElapsed = event.payload.optInt("elapsedSeconds", 0)
                        val now = System.currentTimeMillis()
                        val suggestedStart = now - incomingElapsed * 1_000L
                        _state.value = _state.value.copy(activeTurn = TurnProgressUi(
                            taskId,
                            event.envelope.conversationId,
                            event.payload.optString("state", "running"),
                            maxOf(current?.elapsedSeconds ?: 0, incomingElapsed),
                            minOf(current?.startedAt ?: suggestedStart, suggestedStart),
                            now,
                            event.envelope.sequence,
                        ))
                        updateTaskFromEvent(event, "RUNNING", event.payload.optString("state", "working"), incomingElapsed)
                    }
                }
            }
            MessageType.TURN_COMPLETE -> {
                finishAgentMessage(event)
                updateTaskFromEvent(event, "COMPLETE", "complete", terminal = true)
                scheduleHistoryBackup(event.envelope.conversationId)
                if (_state.value.activeTurn?.taskId == eventTaskId(event)) {
                    _state.value = _state.value.copy(activeTurn = null)
                }
            }
            MessageType.ERROR -> {
                val errorCode = event.payload.optString("code")
                val rawMessage = event.payload.optString("message", "Erro remoto")
                val humanError = humanizeError(event.payload.optString("message", "Erro remoto"))
                val silentSystemError = isSilentSystemError(errorCode, rawMessage)
                if (!silentSystemError) {
                    finishAgentMessage(event, humanError)
                } else {
                    finishAgentMessage(event)
                }
                val cancelled = isCancelledErrorCode(errorCode)
                updateTaskFromEvent(
                    event,
                    if (cancelled) "CANCELLED" else "ERROR",
                    errorPhase(errorCode, rawMessage),
                    error = if (cancelled) null else humanError,
                    terminal = true,
                )
                scheduleHistoryBackup(event.envelope.conversationId)
                _state.value = _state.value.copy(
                    error = if (cancelled || silentSystemError) null else humanError,
                    activeTurn = _state.value.activeTurn
                        ?.takeUnless { it.taskId == eventTaskId(event) },
                )
            }
            else -> Unit
        }
    }

    private suspend fun handleTextDelta(event: DecryptedEvent) {
        val taskId = eventTaskId(event)
        dao.appendAgentDeltaOnce(
            eventId = event.envelope.messageId,
            messageId = "agent-$taskId",
            conversationId = event.envelope.conversationId,
            text = event.payload.optString("text"),
            createdAt = event.envelope.createdAt,
            processedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun finishAgentMessage(event: DecryptedEvent) {
        finishAgentMessage(event, null)
    }

    private suspend fun finishAgentMessage(event: DecryptedEvent, fallbackContent: String?) {
        val messageId = "agent-${eventTaskId(event)}"
        val existing = dao.message(messageId)
        if (existing == null && !fallbackContent.isNullOrBlank()) {
            dao.saveMessage(
                MessageEntity(
                    id = messageId,
                    conversationId = event.envelope.conversationId,
                    role = "agent",
                    content = fallbackContent,
                    createdAt = event.envelope.createdAt,
                    status = "complete",
                )
            )
            return
        }
        dao.updateMessageStatus(messageId, "complete")
    }

    private fun eventTaskId(event: DecryptedEvent): String =
        event.payload.optString("taskId").ifBlank {
            _state.value.activeTurn?.takeIf { it.conversationId == event.envelope.conversationId }?.taskId
                ?: "legacy-${event.envelope.conversationId}"
        }

    private suspend fun updateTaskFromEvent(
        event: DecryptedEvent,
        status: String,
        phase: String,
        elapsedSeconds: Int = event.payload.optInt("elapsedSeconds", 0),
        error: String? = null,
        terminal: Boolean = false,
    ) {
        val taskId = eventTaskId(event)
        val existing = dao.task(taskId)
        val now = System.currentTimeMillis()
        dao.saveTask(
            (existing ?: TaskEntity(
                taskId,
                event.envelope.conversationId,
                event.envelope.deviceId,
                _state.value.selectedProject.orEmpty(),
                "",
                status,
                phase,
                event.envelope.createdAt,
                now,
            )).copy(
                status = status,
                phase = phase,
                elapsedSeconds = maxOf(existing?.elapsedSeconds ?: 0, elapsedSeconds),
                error = error,
                updatedAt = now,
                unread = terminal,
            )
        )
        dao.saveAudit(
            AuditEntity(UUID.randomUUID().toString(), taskId, event.envelope.conversationId, status, phase, now)
        )
    }

    private suspend fun syncSnapshotTasks(event: DecryptedEvent) {
        val tasks = event.payload.optJSONArray("tasks") ?: return
        val terminalTaskIds = mutableSetOf<String>()
        val snapshotTaskIds = mutableSetOf<String>()
        for (index in 0 until tasks.length()) {
            val item = tasks.getJSONObject(index)
            val taskId = item.getString("taskId")
            snapshotTaskIds += taskId
            val existing = dao.task(taskId)
            val status = item.optString("status", "UNKNOWN")
            if (status in setOf("COMPLETE", "ERROR", "CANCELLED")) {
                terminalTaskIds += taskId
            }
            dao.saveTask(
                (existing ?: TaskEntity(
                    taskId,
                    item.getString("conversationId"),
                    event.envelope.deviceId,
                    item.optString("projectId"),
                    "",
                    status,
                    item.optString("phase"),
                    item.optLong("createdAt"),
                    item.optLong("updatedAt"),
                )).copy(
                    status = status,
                    phase = item.optString("phase"),
                    updatedAt = item.optLong("updatedAt"),
                    elapsedSeconds = item.optInt("elapsedSeconds"),
                    error = item.optString("error")
                        .takeIf { !item.isNull("error") && it.isNotBlank() && it != "null" },
                )
            )
        }
        dao.activeTasks(event.envelope.deviceId)
            .filterNot { it.id in snapshotTaskIds }
            .forEach { missing ->
                dao.markTaskMissing(
                    missing.id,
                    "A ponte não reconheceu mais esta tarefa; envie a instrução novamente",
                    System.currentTimeMillis(),
                )
                terminalTaskIds += missing.id
            }
        _state.value.activeTurn?.takeIf { it.taskId in terminalTaskIds }?.let {
            _state.value = _state.value.copy(activeTurn = null)
        }
    }

    private fun humanizeError(message: String): String = when {
        "model" in message.lowercase() -> "O Antigravity não conseguiu carregar o modelo. Verifique a conexão e tente novamente."
        "not logged" in message.lowercase() || "auth" in message.lowercase() -> "A sessão do Antigravity no computador precisa ser renovada."
        "timeout" in message.lowercase() -> "A tarefa excedeu o tempo permitido. Você pode tentar novamente."
        "offline" in message.lowercase() -> "O computador está offline. A instrução não foi executada."
        "quota" in message.lowercase() || "rate limit" in message.lowercase() || "usage limit" in message.lowercase() -> "A cota da IA foi atingida no computador. Aguarde a renovação do limite e tente novamente."
        else -> message
    }

    private fun isCancelledErrorCode(code: String): Boolean =
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true)

    private fun isSilentSystemError(code: String, message: String): Boolean =
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true) ||
            code.equals("TURN_TIMEOUT", ignoreCase = true) ||
            code.equals("SIGNAL_TIMEOUT", ignoreCase = true) ||
            code.equals("EXECUTION_INTERRUPTED", ignoreCase = true) ||
            isTimeoutLikeMessage(message) ||
            "perdeu contato com o processo" in message.lowercase()

    private fun errorPhase(code: String, message: String): String = when {
        code.equals("CANCELLED", ignoreCase = true) ||
            code.equals("USER_CANCELLED", ignoreCase = true) -> "cancelled"
        code.equals("TURN_TIMEOUT", ignoreCase = true) ||
            isTimeoutLikeMessage(message) -> "timeout"
        code.equals("SIGNAL_TIMEOUT", ignoreCase = true) -> "signal_timeout"
        code.equals("EXECUTION_INTERRUPTED", ignoreCase = true) -> "interrupted"
        else -> "error"
    }

    private fun isTimeoutLikeMessage(message: String): Boolean {
        val normalized = message.lowercase()
        return "timeout" in normalized || "timed out" in normalized || "tempo permitido" in normalized
    }

    private fun technicalEventText(event: DecryptedEvent): String = when (event.envelope.type) {
        MessageType.THOUGHT_DELTA -> event.payload.optString("text")
            .ifBlank { event.payload.optString("thought") }
            .ifBlank { event.payload.toString(2) }
        MessageType.TOOL_CALL -> {
            val name = event.payload.optString("name")
                .ifBlank { event.payload.optString("tool") }
                .ifBlank { "Ferramenta" }
            val detail = event.payload.optString("description")
                .ifBlank { event.payload.optString("text") }
            if (detail.isBlank()) name else "$name\n$detail"
        }
        else -> event.payload.toString(2)
    }

    private fun parseProjects(array: org.json.JSONArray): List<RemoteProject> = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val builds = item.optJSONArray("buildProfiles") ?: org.json.JSONArray()
        RemoteProject(item.getString("id"), item.getString("name"), (0 until builds.length()).map { i ->
            val build = builds.getJSONObject(i); BuildSummary(build.getString("id"), build.getString("name"))
        })
    }

    fun selectProject(projectId: String) {
        val state = _state.value
        messageJob?.cancel()
        savedState[SAVED_PROJECT] = projectId
        sessionStore.projectId = projectId
        sessionStore.conversationId = null
        savedState.remove<String>(SAVED_CONVERSATION)
        _state.value = state.copy(
            selectedProject = projectId,
            conversations = emptyList(),
            chat = emptyList(),
            technicalDetails = emptyList(),
            showTechnicalDetails = false,
            projectFiles = emptyList(),
            openedFilePath = null,
            openedFileContent = null,
            gitStatus = "",
            diffStat = "",
            projectDiff = "",
        )
        val device = state.selectedDevice ?: return
        observeConversations(device, projectId)
        launchAction { repository.getProjectStatus(device, projectId, state.conversationId) }
    }

    fun browseProjectFiles(path: String = "") {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val project = state.selectedProject ?: return
        launchAction { repository.listProjectFiles(device, project, state.conversationId, path) }
    }

    fun createProjectDirectory(name: String) {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val project = state.selectedProject ?: return
        if (name.isBlank()) return fail(IllegalArgumentException("Informe o nome da pasta"))
        launchAction { repository.createProjectDirectory(device, project, state.conversationId, state.projectFilesCurrent, name.trim()) }
    }

    fun openProjectFile(path: String) {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val project = state.selectedProject ?: return
        launchAction { repository.readProjectFile(device, project, state.conversationId, path) }
    }

    fun closeProjectFile() { _state.value = _state.value.copy(openedFilePath = null, openedFileContent = null) }

    fun refreshProjectStatus() {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val project = state.selectedProject ?: return
        launchAction { repository.getProjectStatus(device, project, state.conversationId) }
    }

    fun refreshProjects() {
        val device = _state.value.selectedDevice ?: return
        _state.value = _state.value.copy(projectsLoaded = false)
        launchAction { repository.sync(device) }
    }

    fun openDirectoryBrowser() {
        val device = _state.value.selectedDevice
            ?: return fail(IllegalStateException("Selecione um computador"))
        _state.value = _state.value.copy(
            directoryBrowserOpen = true,
            directoryCurrent = null,
            directoryParent = null,
            directoryEntries = emptyList(),
        )
        launchAction { repository.listDirectories(device, null) }
    }

    fun browseDirectory(path: String?) {
        val device = _state.value.selectedDevice ?: return
        launchAction { repository.listDirectories(device, path) }
    }

    fun createDirectory(
        name: String,
        navigate: Boolean = true,
        useAsProject: Boolean = false,
        projectName: String? = null,
    ) {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val parent = state.directoryCurrent ?: return
        if (name.isBlank()) return fail(IllegalArgumentException("Informe o nome da pasta"))
        val effectiveProjectName = projectName?.trim()?.ifBlank { null } ?: name.trim()
        if (useAsProject) {
            _state.value = state.copy(directoryBrowserOpen = false)
            pendingSelectProjectName = effectiveProjectName
        }
        launchAction {
            repository.createDirectory(
                deviceId = device,
                parent = parent,
                name = name.trim(),
                navigate = navigate,
                useAsProject = useAsProject,
                projectName = effectiveProjectName,
            )
        }
    }

    fun closeDirectoryBrowser() {
        _state.value = _state.value.copy(directoryBrowserOpen = false)
    }

    fun addProjectFromDirectory(name: String) {
        val state = _state.value
        val device = state.selectedDevice ?: return
        val root = state.directoryCurrent ?: return
        if (name.isBlank()) return fail(IllegalArgumentException("Informe o nome do projeto"))
        val trimmedName = name.trim()
        _state.value = state.copy(directoryBrowserOpen = false)
        pendingSelectProjectName = trimmedName
        launchAction { repository.addProject(device, trimmedName, root) }
    }

    fun newConversation() {
        val state = _state.value
        val id = UUID.randomUUID().toString()
        messageJob?.cancel()
        savedState[SAVED_CONVERSATION] = id
        sessionStore.conversationId = id
        _state.value = state.copy(
            conversationId = id,
            chat = emptyList(),
            technicalDetails = emptyList(),
            showTechnicalDetails = false,
            activeTurn = null,
            draftText = "",
            buildLog = "",
        )
        if (state.selectedDevice != null && state.selectedProject != null) viewModelScope.launch {
            dao.saveConversation(ConversationEntity(id, state.selectedDevice, state.selectedProject, "Nova conversa", System.currentTimeMillis()))
        }
        observeConversationMessages(id)
    }

    fun selectConversation(id: String) {
        savedState[SAVED_CONVERSATION] = id
        sessionStore.conversationId = id
        val restoredTask = _state.value.tasks.firstOrNull {
            it.conversationId == id && it.status in setOf("QUEUED", "STARTING", "RUNNING", "CANCELLING")
        }
        _state.value = _state.value.copy(
            conversationId = id,
            chat = emptyList(),
            technicalDetails = emptyList(),
            showTechnicalDetails = false,
            activeTurn = _state.value.activeTurn?.takeIf { it.conversationId == id }
                ?: restoredTask?.let {
                    TurnProgressUi(
                        it.id, id, it.phase, it.elapsedSeconds,
                        System.currentTimeMillis() - it.elapsedSeconds * 1_000L,
                    )
                },
            draftText = "",
            buildLog = "",
        )
        observeConversationMessages(id)
    }

    fun renameConversation(id: String, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) {
            dao.renameConversation(id, title.trim(), System.currentTimeMillis())
            scheduleHistoryBackup(id)
        }
    }

    fun archiveConversation(id: String) = viewModelScope.launch {
        dao.archiveConversation(id, System.currentTimeMillis())
        scheduleHistoryBackup(id)
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        val conversation = dao.conversation(id)
        dao.deleteConversation(id)
        historyBackupJobs.remove(id)?.cancel()
        conversation?.let { value ->
            runCatching { repository.deleteConversationBackup(value.deviceId, id) }
        }
    }

    fun setDevForcePro(enabled: Boolean) {
        sessionStore.devForcePro = enabled
        _state.value = _state.value.copy(
            devForcePro = enabled,
            isPro = enabled || _state.value.isPro,
        )
    }

    fun updateProStatus(billingActive: Boolean) {
        val isPro = billingActive || sessionStore.devForcePro
        _state.value = _state.value.copy(
            isPro = isPro,
            devForcePro = sessionStore.devForcePro,
            dailyMessageCount = sessionStore.dailyMessageCount,
        )
    }

    fun toggleTechnicalDetails() {
        _state.value = _state.value.copy(showTechnicalDetails = !_state.value.showTechnicalDetails)
    }

    fun updateDraft(value: String) {
        val conversationId = _state.value.conversationId
        _state.value = _state.value.copy(draftText = value)
        viewModelScope.launch { dao.saveDraft(DraftEntity(conversationId, value)) }
    }

    fun sendPrompt(prompt: String) {
        val state = _state.value
        val device = state.selectedDevice ?: return fail(IllegalStateException("Selecione um computador"))
        val project = state.selectedProject ?: return fail(IllegalStateException("Selecione um projeto"))
        if (state.activeTurn?.conversationId == state.conversationId) {
            return fail(IllegalStateException("Aguarde a tarefa atual terminar ou cancele antes de enviar outra mensagem"))
        }

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (sessionStore.dailyMessageDate != today) {
            sessionStore.dailyMessageDate = today
            sessionStore.dailyMessageCount = 0
        }

        val isProActive = state.isPro || sessionStore.devForcePro
        if (!isProActive && sessionStore.dailyMessageCount >= state.dailyMessageLimit) {
            return fail(IllegalStateException("Limite diário de mensagens gratuito atingido (${state.dailyMessageLimit}/${state.dailyMessageLimit}). Assine o Interestellar Pro para uso ilimitado!"))
        }

        if (!isProActive) {
            sessionStore.dailyMessageCount += 1
        }

        val taskId = UUID.randomUUID().toString()
        _state.value = state.copy(
            chat = state.chat + ChatLine("user", prompt),
            activeTurn = TurnProgressUi(taskId, state.conversationId, "sending", 0),
            draftText = "",
            dailyMessageCount = sessionStore.dailyMessageCount,
        )
        viewModelScope.launch {
            dao.saveConversation(ConversationEntity(state.conversationId, device, project, prompt.take(48), System.currentTimeMillis()))
            dao.saveMessage(MessageEntity(UUID.randomUUID().toString(), state.conversationId, "user", prompt, System.currentTimeMillis(), "sent"))
            dao.saveDraft(DraftEntity(state.conversationId, ""))
            scheduleHistoryBackup(state.conversationId)
        }
        launchAction {
            try {
                val now = System.currentTimeMillis()
                dao.saveTask(TaskEntity(taskId, state.conversationId, device, project, prompt, "QUEUED", "sending", now, now))
                dao.saveAudit(AuditEntity(UUID.randomUUID().toString(), taskId, state.conversationId, "PROMPT", "Instrução enviada", now))
                repository.sendPrompt(
                    device,
                    project,
                    state.conversationId,
                    taskId,
                    prompt,
                    state.selectedModel,
                    state.executionMode,
                )
            } catch (error: Exception) {
                val existing = dao.task(taskId)
                if (existing != null) dao.saveTask(
                    existing.copy(
                        status = "ERROR",
                        phase = "send_failed",
                        error = humanizeError(error.message ?: "Falha ao enviar"),
                        updatedAt = System.currentTimeMillis(),
                        unread = true,
                    )
                )
                _state.value = _state.value.copy(activeTurn = null)
                throw error
            }
        }
    }

    fun cancel() {
        val state = _state.value
        state.selectedDevice?.let {
            _state.value = state.copy(
                activeTurn = state.activeTurn?.copy(state = "cancelling")
            )
            launchAction { repository.cancelTurn(it, state.conversationId, state.activeTurn?.taskId) }
        }
    }

    fun retryTask(task: TaskEntity) {
        if (_state.value.activeTurn != null) return fail(IllegalStateException("Aguarde a tarefa atual terminar"))
        _state.value = _state.value.copy(
            selectedDevice = task.deviceId,
            selectedProject = task.projectId,
            conversationId = task.conversationId,
        )
        sendPrompt(task.prompt)
    }

    fun openTask(task: TaskEntity) {
        _state.value = _state.value.copy(
            selectedDevice = task.deviceId,
            selectedProject = task.projectId,
            conversationId = task.conversationId,
        )
        selectConversation(task.conversationId)
        markTaskRead(task.id)
    }

    fun markTaskRead(taskId: String) = viewModelScope.launch { dao.markTaskRead(taskId) }

    fun setModel(model: String) {
        savedState[SAVED_MODEL] = model
        sessionStore.model = model
        _state.value = _state.value.copy(selectedModel = model)
    }
    fun setExecutionMode(mode: String) { _state.value = _state.value.copy(executionMode = mode) }
    fun build(profileId: String) { val s = _state.value; if (s.selectedDevice != null && s.selectedProject != null) launchAction { repository.runBuild(s.selectedDevice, s.selectedProject, s.conversationId, profileId) } }
    fun cancelBuild() { val s = _state.value; if (s.selectedDevice != null && s.activeBuildId != null) launchAction { repository.cancelBuild(s.selectedDevice, s.conversationId, s.activeBuildId) } }
    fun approve(value: ApprovalUi, approved: Boolean) { val s = _state.value; s.selectedDevice?.let { launchAction { repository.answerApproval(it, value.conversationId, value.id, approved) }; _state.value = s.copy(approvals = s.approvals - value) } }
    fun downloadArtifact(value: ArtifactUi, target: android.net.Uri) { val device = _state.value.selectedDevice ?: return; launchAction { repository.downloadArtifact(device, value.conversationId, value.remoteName, value.keyVersion, target) } }
    fun revokeSelectedDevice() { val device = _state.value.selectedDevice ?: return; launchAction { repository.revoke(device); _state.value = RemoteUiState(signedIn = true) } }
    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun signOut() {
        // Remove authenticated UI before Firebase revokes listener permissions.
        _state.value = RemoteUiState()
        savedState.remove<String>(SAVED_DEVICE)
        savedState.remove<String>(SAVED_PROJECT)
        savedState.remove<String>(SAVED_CONVERSATION)
        sessionStore.clear()
        deviceJob?.cancel()
        eventJobs.values.forEach(Job::cancel)
        historyBackupJobs.values.forEach(Job::cancel)
        conversationJob?.cancel()
        messageJob?.cancel()
        deviceJob = null
        eventJobs.clear()
        historyBackupJobs.clear()
        restoredHistoryDevices.clear()
        conversationJob = null
        messageJob = null
        tasksJob = null
        auditJob = null
        auth.signOut()
    }

    private fun launchAction(action: suspend () -> Unit) = viewModelScope.launch {
        _state.value = _state.value.copy(busy = true, error = null)
        runCatching { action() }.onFailure { error ->
            if (auth.currentUser != null) fail(error)
        }
        _state.value = _state.value.copy(busy = false)
    }
    private fun fail(error: Throwable) { _state.value = _state.value.copy(error = error.message ?: "Erro inesperado", busy = false) }
}
