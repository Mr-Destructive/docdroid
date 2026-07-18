package com.docdroid.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolResult(
    val toolName: String,
    val status: ToolStatus,
    val result: String = "",
    val outputPath: String? = null,
    val error: String? = null,
    val executionTimeMs: Long = 0
)
