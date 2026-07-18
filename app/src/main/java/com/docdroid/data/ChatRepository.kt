package com.docdroid.data

import com.docdroid.model.ChatMessage
import com.docdroid.model.ToolCallResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatRepository {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addUserMessage(content: String, attachments: List<com.docdroid.model.DocumentFile> = emptyList()) {
        val msg = ChatMessage(
            role = com.docdroid.model.Role.USER,
            content = content,
            attachments = attachments
        )
        _messages.value = _messages.value + msg
    }

    fun addAssistantMessage(
        content: String,
        toolCalls: List<ToolCallResult> = emptyList()
    ) {
        val msg = ChatMessage(
            role = com.docdroid.model.Role.ASSISTANT,
            content = content,
            toolCalls = toolCalls
        )
        _messages.value = _messages.value + msg
    }

    fun addSystemMessage(content: String) {
        val msg = ChatMessage(
            role = com.docdroid.model.Role.SYSTEM,
            content = content
        )
        _messages.value = _messages.value + msg
    }

    fun clear() {
        _messages.value = emptyList()
    }
}
