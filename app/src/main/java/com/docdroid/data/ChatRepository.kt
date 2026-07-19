package com.docdroid.data

import android.content.Context
import com.docdroid.data.db.ChatDatabase
import com.docdroid.data.db.ChatMessageEntity
import com.docdroid.model.ChatMessage
import com.docdroid.model.DocumentFile
import com.docdroid.model.Role
import com.docdroid.model.ToolCallResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatRepository(context: Context? = null) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var dao: com.docdroid.data.db.ChatDao? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        if (context != null) {
            val db = ChatDatabase.getInstance(context)
            dao = db.chatDao()
            loadMessages()
        }
    }

    private fun loadMessages() {
        dao?.getAllMessages()?.onEach { entities ->
            _messages.value = entities.map { it.toDomain() }
        }?.launchIn(scope)
    }

    fun addUserMessage(content: String, attachments: List<DocumentFile> = emptyList()) {
        val msg = ChatMessage(
            role = Role.USER,
            content = content,
            attachments = attachments
        )
        _messages.value = _messages.value + msg
        persistMessage(msg)
    }

    fun addAssistantMessage(
        content: String,
        toolCalls: List<ToolCallResult> = emptyList()
    ) {
        val msg = ChatMessage(
            role = Role.ASSISTANT,
            content = content,
            toolCalls = toolCalls
        )
        _messages.value = _messages.value + msg
        persistMessage(msg)
    }

    fun addSystemMessage(content: String) {
        val msg = ChatMessage(
            role = Role.SYSTEM,
            content = content
        )
        _messages.value = _messages.value + msg
        persistMessage(msg)
    }

    fun clear() {
        _messages.value = emptyList()
        scope.launch { dao?.clearAll() }
    }

    private fun persistMessage(msg: ChatMessage) {
        scope.launch {
            dao?.insertMessage(msg.toEntity())
        }
    }

    private fun ChatMessage.toEntity(): ChatMessageEntity {
        return ChatMessageEntity(
            id = id,
            role = role.name,
            content = content,
            attachmentsJson = json.encodeToString(attachments),
            toolCallsJson = json.encodeToString(toolCalls),
            timestamp = timestamp
        )
    }

    private fun ChatMessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            role = try { Role.valueOf(role) } catch (_: Exception) { Role.SYSTEM },
            content = content,
            attachments = try { json.decodeFromString<List<DocumentFile>>(attachmentsJson) } catch (_: Exception) { emptyList() },
            toolCalls = try { json.decodeFromString<List<ToolCallResult>>(toolCallsJson) } catch (_: Exception) { emptyList() },
            timestamp = timestamp
        )
    }
}
