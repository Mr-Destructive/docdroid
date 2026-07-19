package com.docdroid.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val role: String,
    val content: String,
    val attachmentsJson: String = "[]",
    val toolCallsJson: String = "[]",
    val timestamp: Long
)
