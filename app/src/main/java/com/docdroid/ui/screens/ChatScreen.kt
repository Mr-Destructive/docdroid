package com.docdroid.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docdroid.agent.AgentEvent
import com.docdroid.agent.AgentLoop
import com.docdroid.data.ChatRepository
import com.docdroid.model.*
import com.docdroid.ui.components.*
import com.docdroid.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    agentLoop: AgentLoop,
    repository: ChatRepository,
    onFilePicked: (List<DocumentFile>) -> Unit
) {
    val messages by repository.messages.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var pendingFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.map { uri ->
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use {
                it.moveToFirst()
                it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME).let { idx ->
                    it.getString(idx)
                }
            } ?: uri.lastPathSegment ?: "unknown"
            val size = cursor?.use {
                it.moveToFirst()
                it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE).let { idx ->
                    it.getLong(idx)
                }
            } ?: 0L
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            DocumentFile(
                name = name,
                path = uri.toString(),
                mimeType = mimeType,
                size = size,
                uri = uri.toString()
            )
        }
        pendingFiles = pendingFiles + files
        onFilePicked(files)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("DocDroid", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            if (messages.isEmpty() && pendingFiles.isEmpty()) {
                EmptyState()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
            }

            if (pendingFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pendingFiles.forEach { file ->
                        FileAttachment(
                            file = file,
                            onRemove = {
                                pendingFiles = pendingFiles.filter { it.id != file.id }
                            }
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Primary
                )
            }

            MessageInput(
                onSendMessage = { text ->
                    if (text.isBlank()) return@MessageInput
                    isLoading = true
                    repository.addUserMessage(text, pendingFiles)
                    val files = pendingFiles
                    pendingFiles = emptyList()

                    scope.launch {
                        agentLoop.processMessage(text, files).collectLatest { event ->
                            when (event) {
                                is AgentEvent.Thinking -> {
                                    repository.addSystemMessage(event.message)
                                }
                                is AgentEvent.Response -> {
                                    repository.addAssistantMessage(
                                        content = event.message,
                                        toolCalls = event.results.map { r ->
                                            ToolCallResult(
                                                toolName = r.toolName,
                                                status = r.status,
                                                result = r.result,
                                                outputPath = r.outputPath,
                                                error = r.error,
                                                executionTimeMs = r.executionTimeMs
                                            )
                                        }
                                    )
                                    isLoading = false
                                }
                                is AgentEvent.Error -> {
                                    repository.addAssistantMessage("Error: ${event.message}")
                                    isLoading = false
                                }
                                is AgentEvent.CodeGenerated -> {
                                    repository.addSystemMessage("Generated Python code:\n${event.code.take(500)}")
                                }
                                else -> {}
                            }
                        }
                    }
                },
                onAttachFiles = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DocDroid",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your AI document assistant",
                fontSize = 16.sp,
                color = ThinkingColor
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Attach files and tell me what to do.\n\n" +
                        "Examples:\n" +
                        "- \"Merge these PDFs and add a DRAFT watermark\"\n" +
                        "- \"Resize this image to 800x600\"\n" +
                        "- \"Convert this DOCX to PDF\"\n" +
                        "- \"Extract text from page 1-3 of this PDF\"\n" +
                        "- \"Compress this image to under 500KB\"",
                fontSize = 13.sp,
                color = OnBackground,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
