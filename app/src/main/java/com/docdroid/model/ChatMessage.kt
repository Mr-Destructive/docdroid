package com.docdroid.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val attachments: List<DocumentFile> = emptyList(),
    val toolCalls: List<ToolCallResult> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class Role { USER, ASSISTANT, SYSTEM }

@Serializable
data class DocumentFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mimeType: String,
    val size: Long,
    val uri: String = ""
)

@Serializable
data class ToolCallResult(
    val toolName: String,
    val arguments: Map<String, String> = emptyMap(),
    val status: ToolStatus,
    val result: String = "",
    val outputPath: String? = null,
    val error: String? = null,
    val executionTimeMs: Long = 0
)

@Serializable
enum class ToolStatus { PENDING, RUNNING, SUCCESS, FAILED, CODE_GENERATED }
