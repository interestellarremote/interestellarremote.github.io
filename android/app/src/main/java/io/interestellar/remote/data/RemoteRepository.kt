package io.interestellar.remote.data

import android.net.Uri
import android.content.Context
import io.interestellar.remote.security.CryptoEngine
import io.interestellar.remote.security.RootKeyStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

data class DeviceSummary(val id: String, val name: String, val online: Boolean, val lastSeen: Long)
data class DecryptedEvent(val envelope: Envelope, val payload: JSONObject)
data class HistoryBackup(val conversation: ConversationEntity, val messages: List<MessageEntity>)
data class AccessStatus(
    val proActive: Boolean,
    val dailyMessageCount: Int,
    val dailyMessageLimit: Int,
    val quotaDate: String,
    val productId: String? = null,
)

@Singleton
class RemoteRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage,
    private val keyStore: RootKeyStore,
    private val sequences: SequenceStore,
    @ApplicationContext private val context: Context,
) {
    private fun requireUid(): String = requireNotNull(auth.currentUser?.uid) { "Faça login primeiro" }

    suspend fun claimPairing(payload: PairingPayload) {
        require(payload.expiresAt > System.currentTimeMillis()) { "QR Code expirado" }
        requireNotNull(auth.currentUser) { "Faça login antes de parear" }
        functions.getHttpsCallable("claimPairing").call(
            mapOf("pairingId" to payload.pairingId, "deviceId" to payload.deviceId, "secret" to payload.secret)
        ).await()
        keyStore.save(payload.deviceId, payload.keyVersion, CryptoEngine.decode(payload.rootKey))
    }

    suspend fun registerPushToken(tokenOverride: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        val token = tokenOverride ?: FirebaseMessaging.getInstance().token.await()
        val key = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
        database.getReference("users/$uid/fcmTokens/$key").setValue(token).await()
    }

    fun devices(): Flow<List<DeviceSummary>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: run { trySend(emptyList()); close(); return@callbackFlow }
        val deviceRefs = database.getReference("users/$uid/devices")
        val statuses = mutableMapOf<String, Pair<Boolean, Long>>()
        val statusListeners = mutableMapOf<String, Pair<com.google.firebase.database.DatabaseReference, ValueEventListener>>()
        var deviceIds = emptyList<String>()

        fun emitDevices() {
            trySend(deviceIds.map { id ->
                val status = statuses[id] ?: (false to 0L)
                DeviceSummary(id, "Computador ${id.take(6)}", status.first, status.second)
            })
        }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                deviceIds = snapshot.children.mapNotNull { it.key }
                val removed = statusListeners.keys - deviceIds.toSet()
                removed.forEach { id ->
                    statusListeners.remove(id)?.let { (reference, statusListener) ->
                        reference.removeEventListener(statusListener)
                    }
                    statuses.remove(id)
                }
                deviceIds.filterNot(statusListeners::containsKey).forEach { id ->
                    val reference = database.getReference("deviceStatus/$id")
                    val statusListener = object : ValueEventListener {
                        override fun onDataChange(status: DataSnapshot) {
                            statuses[id] =
                                (status.child("online").getValue(Boolean::class.java) ?: false) to
                                (status.child("lastSeen").getValue(Long::class.java) ?: 0L)
                            emitDevices()
                        }
                        override fun onCancelled(error: DatabaseError) {
                            close(error.toException())
                        }
                    }
                    statusListeners[id] = reference to statusListener
                    reference.addValueEventListener(statusListener)
                }
                emitDevices()
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        deviceRefs.addValueEventListener(listener)
        awaitClose {
            deviceRefs.removeEventListener(listener)
            statusListeners.values.forEach { (reference, statusListener) ->
                reference.removeEventListener(statusListener)
            }
        }
    }

    fun events(deviceId: String): Flow<DecryptedEvent> = callbackFlow {
        val reference = database.getReference("mailboxes/$deviceId/events")
        val query = reference.orderByChild("sequence")
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val envelope = Envelope.fromMap(snapshot.value as Map<String, Any>)
                    val rootKey = requireNotNull(keyStore.load(deviceId, envelope.keyVersion))
                    DecryptedEvent(envelope, CryptoEngine.decrypt(rootKey, envelope))
                }.onSuccess { event -> trySend(event) }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onChildRemoved(snapshot: DataSnapshot) = Unit
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        query.addChildEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }.buffer(Channel.UNLIMITED)

    suspend fun pendingEvents(deviceId: String): List<DecryptedEvent> {
        requireNotNull(auth.currentUser) { "Faça login para sincronizar as mensagens" }
        val snapshot = database.getReference("mailboxes/$deviceId/events").get().await()
        return snapshot.children.mapNotNull { child ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                val envelope = Envelope.fromMap(child.value as Map<String, Any>)
                val rootKey = requireNotNull(keyStore.load(deviceId, envelope.keyVersion))
                DecryptedEvent(envelope, CryptoEngine.decrypt(rootKey, envelope))
            }.getOrNull()
        }.sortedBy { it.envelope.sequence }
    }

    suspend fun backupConversation(conversation: ConversationEntity, messages: List<MessageEntity>) {
        val uid = auth.currentUser?.uid ?: return
        val keyVersion = keyStore.currentVersion(conversation.deviceId)
        val rootKey = keyStore.load(conversation.deviceId, keyVersion) ?: return
        val payload = JSONObject()
            .put("schema", 1)
            .put("conversation", JSONObject().apply {
                put("id", conversation.id)
                put("deviceId", conversation.deviceId)
                put("projectId", conversation.projectId)
                put("title", conversation.title)
                put("updatedAt", conversation.updatedAt)
                put("archived", conversation.archived)
            })
            .put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("id", message.id)
                        put("conversationId", message.conversationId)
                        put("role", message.role)
                        put("content", message.content)
                        put("createdAt", message.createdAt)
                        put("status", message.status)
                    })
                }
            })
        val encrypted = CryptoEngine.encryptBackup(
            rootKey, conversation.deviceId, conversation.id, keyVersion, payload,
        )
        database.getReference("users/$uid/history/${conversation.deviceId}/${conversation.id}")
            .setValue(
                mapOf(
                    "schema" to 1,
                    "keyVersion" to keyVersion,
                    "updatedAt" to System.currentTimeMillis(),
                    "nonce" to encrypted.nonce,
                    "ciphertext" to encrypted.ciphertext,
                )
            ).await()
    }

    suspend fun restoreConversationBackups(deviceIds: Collection<String>): List<HistoryBackup> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val allowedDevices = deviceIds.toSet()
        if (allowedDevices.isEmpty()) return emptyList()
        val snapshot = database.getReference("users/$uid/history").get().await()
        return snapshot.children
            .filter { it.key in allowedDevices }
            .flatMap { deviceSnapshot ->
                val deviceId = deviceSnapshot.key ?: return@flatMap emptyList()
                deviceSnapshot.children.mapNotNull { conversationSnapshot ->
                    val conversationId = conversationSnapshot.key ?: return@mapNotNull null
                    val keyVersion = conversationSnapshot.child("keyVersion").getValue(Long::class.java)?.toInt() ?: return@mapNotNull null
                    val nonce = conversationSnapshot.child("nonce").getValue(String::class.java) ?: return@mapNotNull null
                    val ciphertext = conversationSnapshot.child("ciphertext").getValue(String::class.java) ?: return@mapNotNull null
                    runCatching {
                        val rootKey = requireNotNull(keyStore.load(deviceId, keyVersion))
                        val payload = CryptoEngine.decryptBackup(
                            rootKey, deviceId, conversationId, keyVersion, nonce, ciphertext,
                        )
                        val conversationJson = payload.getJSONObject("conversation")
                        val messagesJson = payload.optJSONArray("messages") ?: JSONArray()
                        val messages = (0 until messagesJson.length()).map { index ->
                            val message = messagesJson.getJSONObject(index)
                            MessageEntity(
                                id = message.getString("id"),
                                conversationId = message.getString("conversationId"),
                                role = message.getString("role"),
                                content = message.getString("content"),
                                createdAt = message.getLong("createdAt"),
                                status = message.getString("status"),
                            )
                        }
                        HistoryBackup(
                            ConversationEntity(
                                id = conversationJson.getString("id"),
                                deviceId = conversationJson.getString("deviceId"),
                                projectId = conversationJson.getString("projectId"),
                                title = conversationJson.getString("title"),
                                updatedAt = conversationJson.getLong("updatedAt"),
                                archived = conversationJson.optBoolean("archived", false),
                            ),
                            messages,
                        )
                    }.getOrNull()
                }
            }
    }

    suspend fun deleteConversationBackup(deviceId: String, conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        database.getReference("users/$uid/history/$deviceId/$conversationId").removeValue().await()
    }

    suspend fun acknowledgeEvent(deviceId: String, messageId: String) {
        database.getReference("mailboxes/$deviceId/events/$messageId").removeValue().await()
    }

    suspend fun acknowledgeEvents(deviceId: String, messageIds: Collection<String>) {
        if (messageIds.isEmpty()) return
        val removals = messageIds.associateWith { null }
        database.getReference("mailboxes/$deviceId/events").updateChildren(removals).await()
    }

    private suspend fun buildEnvelope(
        deviceId: String,
        conversationId: String,
        type: MessageType,
        payload: JSONObject,
    ): Envelope {
        val online = database.getReference("deviceStatus/$deviceId/online").get().await().getValue(Boolean::class.java) == true
        require(online) { "O computador está offline; o comando não foi enfileirado" }
        val keyVersion = keyStore.currentVersion(deviceId)
        val rootKey = requireNotNull(keyStore.load(deviceId, keyVersion)) { "Dispositivo precisa ser pareado novamente" }
        val sequence = sequences.next(deviceId, conversationId)
        return CryptoEngine.encrypt(rootKey, deviceId, conversationId, sequence, type, payload, keyVersion = keyVersion)
    }

    private suspend fun writeEnvelope(envelope: Envelope) {
        database.getReference("mailboxes/${envelope.deviceId}/commands/${envelope.messageId}").setValue(envelope.toMap()).await()
    }

    suspend fun send(
        deviceId: String,
        conversationId: String,
        type: MessageType,
        payload: JSONObject,
    ) {
        val envelope = buildEnvelope(deviceId, conversationId, type, payload)
        writeEnvelope(envelope)
    }

    suspend fun getAccessStatus(): AccessStatus {
        val data = functions.getHttpsCallable("getAccessStatus").call().await().data as? Map<*, *>
            ?: error("Resposta inválida do servidor para status de acesso")
        return AccessStatus(
            proActive = data["proActive"] as? Boolean ?: false,
            dailyMessageCount = (data["dailyMessageCount"] as? Number)?.toInt() ?: 0,
            dailyMessageLimit = (data["dailyMessageLimit"] as? Number)?.toInt() ?: 10,
            quotaDate = data["quotaDate"] as? String ?: "",
            productId = data["productId"] as? String,
        )
    }

    suspend fun syncSubscriptionPurchase(purchaseToken: String, productId: String?): AccessStatus {
        val data = functions.getHttpsCallable("syncSubscriptionPurchase").call(
            mapOf(
                "purchaseToken" to purchaseToken,
                "productId" to productId,
            ),
        ).await().data as? Map<*, *>
            ?: error("Resposta inválida do servidor para a assinatura")
        val status = getAccessStatus()
        return status.copy(productId = data["productId"] as? String ?: status.productId)
    }

    suspend fun sync(deviceId: String) = send(deviceId, "system", MessageType.SYNC, JSONObject())

    suspend fun listDirectories(deviceId: String, path: String?) =
        send(
            deviceId,
            "filesystem",
            MessageType.LIST_DIRECTORIES,
            JSONObject().put("metadata", JSONObject().apply { if (path != null) put("path", path) }),
        )

    suspend fun createDirectory(
        deviceId: String,
        parent: String,
        name: String,
        navigate: Boolean = true,
        useAsProject: Boolean = false,
        projectName: String? = null,
    ) =
        send(
            deviceId,
            "filesystem",
            MessageType.CREATE_DIRECTORY,
            JSONObject().put(
                "metadata",
                JSONObject()
                    .put("parent", parent)
                    .put("name", name)
                    .put("navigate", navigate)
                    .put("use_as_project", useAsProject)
                    .apply {
                        if (!projectName.isNullOrBlank()) put("projectName", projectName)
                    },
            ),
        )

    suspend fun addProject(deviceId: String, name: String, root: String) =
        send(
            deviceId,
            "filesystem",
            MessageType.ADD_PROJECT,
            JSONObject().put(
                "metadata",
                JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", name)
                    .put("root", root),
            ),
        )

    suspend fun listProjectFiles(deviceId: String, projectId: String, conversationId: String, path: String = "") =
        send(
            deviceId,
            conversationId,
            MessageType.LIST_PROJECT_FILES,
            JSONObject().put("projectId", projectId).put("metadata", JSONObject().put("path", path)),
        )

    suspend fun createProjectDirectory(deviceId: String, projectId: String, conversationId: String, path: String, name: String) =
        send(
            deviceId,
            conversationId,
            MessageType.CREATE_PROJECT_DIRECTORY,
            JSONObject().put("projectId", projectId).put("metadata", JSONObject().put("path", path).put("name", name)),
        )

    suspend fun readProjectFile(deviceId: String, projectId: String, conversationId: String, path: String) =
        send(
            deviceId,
            conversationId,
            MessageType.READ_PROJECT_FILE,
            JSONObject().put("projectId", projectId).put("metadata", JSONObject().put("path", path)),
        )

    suspend fun getProjectStatus(deviceId: String, projectId: String, conversationId: String) =
        send(
            deviceId,
            conversationId,
            MessageType.GET_PROJECT_STATUS,
            JSONObject().put("projectId", projectId),
        )

    suspend fun sendPrompt(
        deviceId: String,
        projectId: String,
        conversationId: String,
        taskId: String,
        prompt: String,
        model: String? = null,
        executionMode: String = "autonomous_project",
        hasPlayProAccess: Boolean = false,
    ): AccessStatus {
        android.util.Log.d("ModelTrace", "3. Inside repository.sendPrompt - model parameter: $model")
        requireUid()
        val envelope = buildEnvelope(
            deviceId,
            conversationId,
            MessageType.SEND_PROMPT,
            JSONObject()
                .put("taskId", taskId)
                .put("projectId", projectId)
                .put("prompt", prompt)
                .put("executionMode", executionMode)
                .apply { if (model != null) put("model", model) },
        )
        val data = functions.getHttpsCallable("dispatchPrompt").call(
            mapOf("envelope" to envelope.toMap(), "hasPlayProAccess" to hasPlayProAccess),
        ).await().data as? Map<*, *>
            ?: error("Resposta inválida do servidor para o despacho do prompt")
        return AccessStatus(
            proActive = data["proActive"] as? Boolean ?: false,
            dailyMessageCount = (data["dailyMessageCount"] as? Number)?.toInt() ?: 0,
            dailyMessageLimit = (data["dailyMessageLimit"] as? Number)?.toInt() ?: 10,
            quotaDate = data["quotaDate"] as? String ?: "",
            productId = data["productId"] as? String,
        )
    }

    suspend fun cancelTurn(deviceId: String, conversationId: String, taskId: String?) =
        send(
            deviceId,
            conversationId,
            MessageType.CANCEL_TURN,
            JSONObject().apply { if (taskId != null) put("taskId", taskId) },
        )

    suspend fun runBuild(deviceId: String, projectId: String, conversationId: String, profileId: String) =
        send(deviceId, conversationId, MessageType.RUN_BUILD, JSONObject().put("projectId", projectId).put("buildProfileId", profileId))

    suspend fun cancelBuild(deviceId: String, conversationId: String, buildId: String) =
        send(deviceId, conversationId, MessageType.CANCEL_BUILD, JSONObject().put("metadata", JSONObject().put("buildId", buildId)))

    suspend fun answerApproval(deviceId: String, conversationId: String, approvalId: String, approved: Boolean) =
        send(deviceId, conversationId, MessageType.APPROVAL_DECISION, JSONObject().put("approvalId", approvalId).put("approved", approved))

    suspend fun downloadArtifact(deviceId: String, conversationId: String, remoteName: String, keyVersion: Int, target: Uri) {
        val snapshot = storage.reference.child("artifacts/$deviceId/$conversationId/$remoteName").stream.await()
        val rootKey = requireNotNull(keyStore.load(deviceId, keyVersion)) { "Chave do dispositivo ausente" }
        val outStream = context.contentResolver.openOutputStream(target, "w") ?: error("Não foi possível abrir o destino")
        
        outStream.use { output ->
            snapshot.stream.use { input ->
                CryptoEngine.decryptArtifactStream(rootKey, deviceId, conversationId, remoteName, input, output, keyVersion)
            }
        }
    }

    suspend fun revoke(deviceId: String) {
        functions.getHttpsCallable("revokeDevice").call(mapOf("deviceId" to deviceId)).await()
        keyStore.remove(deviceId)
    }
}

