package com.docdroid.harness

/**
 * Result of a tool execution.
 */
sealed class ToolResult {
    data class Success(val message: String, val filePath: String? = null) : ToolResult()
    data class Error(val message: String, val toolName: String = "") : ToolResult()

    val isSuccess: Boolean get() = this is Success
    val displayMessage: String
        get() = when (this) {
            is Success -> message
            is Error -> "Error: $message"
        }
}
