package com.docdroid.agent

import com.docdroid.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed class AgentEvent {
    data class Thinking(val message: String) : AgentEvent()
    data class ToolCallStarted(val toolCall: ToolCall, val index: Int, val total: Int) : AgentEvent()
    data class ToolCallCompleted(val result: ToolResult) : AgentEvent()
    data class Response(val message: String, val results: List<ToolResult> = emptyList()) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
    data class CodeGenerated(val code: String, val language: String = "python") : AgentEvent()
    data class Status(val message: String) : AgentEvent()
}

class AgentLoop(
    private val needleAgent: NeedleEngine,
    private val dispatcher: ToolDispatcher,
    private val pythonCodeGenerator: PythonCodeGenerator
) {

    fun processMessage(query: String, attachedFiles: List<DocumentFile> = emptyList()): Flow<AgentEvent> = flow {
        emit(AgentEvent.Thinking("Analyzing your request..."))

        val toolsJson = buildToolsJson()
        val fullQuery = buildQuery(query, attachedFiles)

        val toolCallsResult = needleAgent.query(fullQuery, toolsJson)

        if (toolCallsResult.isFailure) {
            emit(AgentEvent.Error("Needle query failed: ${toolCallsResult.exceptionOrNull()?.message}"))
            return@flow
        }

        val toolCalls = toolCallsResult.getOrNull() ?: emptyList()

        if (toolCalls.isEmpty()) {
            emit(AgentEvent.Response(
                "I couldn't determine a specific operation for your request. " +
                "Here's what I can help with: PDF operations (merge, split, watermark, encrypt...), " +
                "Image operations (resize, crop, filter, convert...), " +
                "Document conversion, and more. Try being more specific about what you'd like to do."
            ))
            return@flow
        }

        emit(AgentEvent.Thinking("Found ${toolCalls.size} operation(s) to execute"))

        val results = mutableListOf<ToolResult>()

        toolCalls.forEachIndexed { index, toolCall ->
            emit(AgentEvent.ToolCallStarted(toolCall, index + 1, toolCalls.size))

            val result = dispatcher.dispatch(toolCall)
            results.add(result)
            emit(AgentEvent.ToolCallCompleted(result))

            if (result.status == ToolStatus.FAILED && shouldGenerateCode(toolCall)) {
                emit(AgentEvent.Thinking("Predefined tool failed, generating Python code..."))
                val code = pythonCodeGenerator.generate(query, attachedFiles, toolCall)
                emit(AgentEvent.CodeGenerated(code))
            }
        }

        val responseMessage = formatResults(toolCalls, results)
        emit(AgentEvent.Response(responseMessage, results))
    }

    private fun buildQuery(query: String, files: List<DocumentFile>): String {
        if (files.isEmpty()) return query

        val fileContext = files.joinToString("\n") { f ->
            "- ${f.name} (${f.mimeType}, ${formatSize(f.size)}): ${f.path}"
        }
        return "Files available:\n$fileContext\n\nUser request: $query"
    }

    private fun shouldGenerateCode(toolCall: ToolCall): Boolean {
        val complexTools = setOf("execute_python", "create_pdf", "batch_rename")
        return toolCall.name !in complexTools
    }

    private fun formatResults(toolCalls: List<ToolCall>, results: List<ToolResult>): String {
        if (results.isEmpty()) return "No operations were executed."

        val sb = StringBuilder()
        val successCount = results.count { it.status == ToolStatus.SUCCESS }
        val failCount = results.count { it.status == ToolStatus.FAILED }

        sb.appendLine("Completed $successCount/${results.size} operation(s).")
        if (failCount > 0) {
            sb.appendLine("$failCount operation(s) failed.")
        }
        sb.appendLine()

        results.forEach { result ->
            val status = if (result.status == ToolStatus.SUCCESS) "✓" else "✗"
            sb.appendLine("$status ${result.toolName}")
            if (result.outputPath != null) {
                sb.appendLine("  → ${result.outputPath}")
            }
            if (result.result.isNotEmpty()) {
                sb.appendLine("  ${result.result.take(200)}")
            }
            if (result.error != null) {
                sb.appendLine("  Error: ${result.error}")
            }
        }

        return sb.toString()
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
    }
}
