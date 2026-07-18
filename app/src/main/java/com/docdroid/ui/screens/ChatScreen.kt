package com.docdroid.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private data class SuggestionChip(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val prompt: String
)

private val suggestionChips = listOf(
    SuggestionChip(Icons.Default.PictureAsPdf, "Merge PDFs", "Merge these PDFs into one document"),
    SuggestionChip(Icons.Default.PictureAsPdf, "Add Watermark", "Add a DRAFT watermark to all pages"),
    SuggestionChip(Icons.Default.PictureAsPdf, "Extract Text", "Extract text from this PDF"),
    SuggestionChip(Icons.Default.Image, "Resize Image", "Resize this image to 800x600"),
    SuggestionChip(Icons.Default.Image, "Compress Image", "Compress this image to under 500KB"),
    SuggestionChip(Icons.Default.TextSnippet, "DOCX to PDF", "Convert this Word document to PDF"),
    SuggestionChip(Icons.Default.TableChart, "Read Spreadsheet", "Read and summarize this spreadsheet"),
    SuggestionChip(Icons.Default.Code, "Run Python", "Run Python code to process my files"),
)

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
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val hasLaunched = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasLaunched.value && messages.isEmpty()) {
            hasLaunched.value = true
            repository.addAssistantMessage(
                "Hey! I'm DocDroid, your AI document assistant powered by Needle.\n\n" +
                "Attach any file and tell me what to do. I can handle PDFs, images, Word docs, spreadsheets, " +
                "presentations, audio, video, and more.\n\n" +
                "Try attaching a file and tapping one of the suggestions below!"
            )
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("DocDroid", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                "Powered by Needle",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty()) {
                    item { WelcomeHeader() }
                }

                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }

                if (messages.size <= 1) {
                    item {
                        SuggestionChipsRow(
                            chips = suggestionChips,
                            onChipClick = { prompt ->
                                inputText = prompt
                            },
                            hasFiles = pendingFiles.isNotEmpty(),
                            onAttachFiles = { filePickerLauncher.launch(arrayOf("*/*")) }
                        )
                    }
                }
            }

            if (pendingFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
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
                text = inputText,
                onTextChange = { inputText = it },
                onSendMessage = { text ->
                    if (text.isBlank()) return@MessageInput
                    isLoading = true
                    repository.addUserMessage(text, pendingFiles)
                    val files = pendingFiles
                    pendingFiles = emptyList()
                    inputText = ""

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
private fun WelcomeHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Primary)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "What can I help with?",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = OnBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Attach files and I'll take care of the rest.",
            fontSize = 14.sp,
            color = ThinkingColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuggestionChipsRow(
    chips: List<SuggestionChip>,
    onChipClick: (String) -> Unit,
    hasFiles: Boolean,
    onAttachFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!hasFiles) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAttachFiles() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Attach a file to get started",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = OnBackground
                        )
                        Text(
                            "PDFs, images, docs, spreadsheets, audio, video...",
                            fontSize = 12.sp,
                            color = ThinkingColor
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = ThinkingColor
                    )
                }
            }
        }

        Text(
            "Quick actions",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = ThinkingColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        val rows = chips.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { chip ->
                    SuggestionChipItem(
                        chip = chip,
                        onClick = { onChipClick(chip.prompt) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SuggestionChipItem(
    chip: SuggestionChip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = chip.icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = chip.label,
                fontSize = 13.sp,
                color = OnBackground
            )
        }
    }
}
