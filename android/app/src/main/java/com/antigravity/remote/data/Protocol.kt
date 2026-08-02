package com.antigravity.remote.data

import org.json.JSONObject
import java.util.UUID

enum class MessageType {
    CREATE_CONVERSATION, ARCHIVE_CONVERSATION, SEND_PROMPT, CANCEL_TURN,
    RUN_BUILD, CANCEL_BUILD, APPROVAL_DECISION, LIST_DIRECTORIES, CREATE_DIRECTORY, ADD_PROJECT,
    LIST_PROJECT_FILES, CREATE_PROJECT_DIRECTORY, READ_PROJECT_FILE, GET_PROJECT_STATUS, SYNC,
    SNAPSHOT, TEXT_DELTA, THOUGHT_DELTA, TOOL_CALL, APPROVAL_REQUEST,
    BUILD_LOG, BUILD_RESULT, ARTIFACT, TURN_COMPLETE, ERROR, HEARTBEAT, DIRECTORY_LIST,
    PROJECT_FILE_LIST, PROJECT_FILE_CONTENT, PROJECT_STATUS, ACK
}

data class Envelope(
    val version: Int = 1,
    val messageId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val conversationId: String,
    val sequence: Long,
    val type: MessageType,
    val createdAt: Long,
    val expiresAt: Long,
    val keyVersion: Int = 1,
    val nonce: String,
    val ciphertext: String,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "version" to version, "messageId" to messageId, "deviceId" to deviceId,
        "conversationId" to conversationId, "sequence" to sequence, "type" to type.name,
        "createdAt" to createdAt, "expiresAt" to expiresAt, "keyVersion" to keyVersion,
        "nonce" to nonce, "ciphertext" to ciphertext,
    )

    companion object {
        fun fromMap(value: Map<*, *>): Envelope = Envelope(
            version = (value["version"] as Number).toInt(),
            messageId = value["messageId"].toString(),
            deviceId = value["deviceId"].toString(),
            conversationId = value["conversationId"].toString(),
            sequence = (value["sequence"] as Number).toLong(),
            type = MessageType.valueOf(value["type"].toString()),
            createdAt = (value["createdAt"] as Number).toLong(),
            expiresAt = (value["expiresAt"] as Number).toLong(),
            keyVersion = (value["keyVersion"] as Number).toInt(),
            nonce = value["nonce"].toString(),
            ciphertext = value["ciphertext"].toString(),
        )
    }
}

data class PairingPayload(
    val deviceId: String,
    val deviceName: String,
    val pairingId: String,
    val secret: String,
    val rootKey: String,
    val keyVersion: Int,
    val expiresAt: Long,
) {
    companion object {
        fun fromJson(json: JSONObject) = PairingPayload(
            json.getString("deviceId"), json.getString("deviceName"),
            json.getString("pairingId"), json.getString("secret"),
            json.getString("rootKey"), json.optInt("keyVersion", 1), json.getLong("expiresAt")
        )
    }
}

data class RemoteProject(val id: String, val name: String, val buildProfiles: List<BuildSummary>)
data class BuildSummary(val id: String, val name: String)
