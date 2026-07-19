package com.docdroid.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.docdroid.engine.NeedleEngine
import com.docdroid.harness.ToolDefinitions
import com.docdroid.harness.ToolRegistry
import com.docdroid.model.Message
import com.docdroid.model.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates the full pipeline:
 *   user input → Needle routing → tool execution → response
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val needleEngine = NeedleEngine(application)
    private val toolRegistry = ToolRegistry(application)

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _engineStatus = MutableStateFlow<EngineStatus>(EngineStatus.Loading)
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()

    init {
        viewModelScope.launch {
            val success = needleEngine.init()
            _engineStatus.value = if (success) {
                EngineStatus.Ready
            } else {
                EngineStatus.Error("Failed to load Needle model")
            }
        }
    }

    /**
     * Process a user message through the full pipeline.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMessage = Message(content = text, isUser = true)
        _messages.value = _messages.value + userMessage

        viewModelScope.launch {
            _isLoading.value = true

            // Add processing status message
            val processingMsg = Message(
                content = "Thinking...",
                isUser = false,
                status = MessageStatus.PROCESSING
            )
            _messages.value = _messages.value + processingMsg

            try {
                // Step 1: Route through Needle
                val toolCallJson = needleEngine.route(text, ToolDefinitions.JSON)

                if (toolCallJson == null || toolCallJson.isBlank() || toolCallJson == "[]") {
                    updateLastMessage(
                        "I couldn't determine which tool to use for that request. " +
                        "Try being more specific, like:\n" +
                        "• \"Merge file1.pdf and file2.pdf\"\n" +
                        "• \"Extract text from contract.pdf\"\n" +
                        "• \"Rotate pages 1-3 by 90 degrees\"",
                        MessageStatus.ERROR
                    )
                    return@launch
                }

                // Step 2: Execute the tool
                updateLastMessage("Executing...", MessageStatus.TOOL_EXECUTING)

                val result = toolRegistry.execute(toolCallJson)

                // Step 3: Display result
                if (result.isSuccess) {
                    updateLastMessage(result.displayMessage, MessageStatus.DELIVERED)
                } else {
                    updateLastMessage(result.displayMessage, MessageStatus.ERROR)
                }

            } catch (e: Exception) {
                updateLastMessage(
                    "Something went wrong: ${e.message}",
                    MessageStatus.ERROR
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun updateLastMessage(content: String, status: MessageStatus) {
        _messages.value = _messages.value.dropLast(1) + Message(
            content = content,
            isUser = false,
            status = status
        )
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        needleEngine.destroy()
    }

    sealed class EngineStatus {
        data object Loading : EngineStatus()
        data object Ready : EngineStatus()
        data class Error(val message: String) : EngineStatus()
    }
}
