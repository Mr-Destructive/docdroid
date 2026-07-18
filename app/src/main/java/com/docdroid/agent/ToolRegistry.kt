package com.docdroid.agent

import com.docdroid.model.ToolResult
import com.docdroid.model.ToolStatus

class ToolRegistry {
    private val handlers = HashMap<String, suspend (Map<String, String>) -> ToolResult>()

    fun register(toolName: String, handler: suspend (Map<String, String>) -> ToolResult) {
        handlers[toolName] = handler
    }

    fun hasTool(name: String): Boolean = handlers.containsKey(name)

    suspend fun execute(toolName: String, arguments: Map<String, String>): ToolResult {
        val handler = handlers[toolName]
            ?: return ToolResult(
                toolName = toolName,
                status = ToolStatus.FAILED,
                error = "Unknown tool: $toolName"
            )
        return try {
            handler(arguments)
        } catch (e: Exception) {
            ToolResult(
                toolName = toolName,
                status = ToolStatus.FAILED,
                error = "${e::class.simpleName}: ${e.message}"
            )
        }
    }

    fun registeredTools(): Set<String> = handlers.keys.toSet()
}
