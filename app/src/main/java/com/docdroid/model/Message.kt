package com.docdroid.model

import java.util.UUID

/**
 * A single message in the chat conversation.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.DELIVERED
)

enum class MessageStatus {
    DELIVERED,
    PROCESSING,
    TOOL_EXECUTING,
    ERROR
}
