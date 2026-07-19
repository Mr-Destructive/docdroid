package com.docdroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docdroid.ui.components.InputBar
import com.docdroid.ui.components.MessageBubble
import com.docdroid.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "DocDroid",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        val statusText = when (engineStatus) {
                            is ChatViewModel.EngineStatus.Loading -> "Loading model..."
                            is ChatViewModel.EngineStatus.Ready -> "Ready"
                            is ChatViewModel.EngineStatus.Error -> "Error"
                        }
                        Text(
                            statusText,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear chat")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Message list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        WelcomeMessage(engineStatus)
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            // Input bar
            InputBar(
                onSend = { viewModel.sendMessage(it) },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun WelcomeMessage(status: ChatViewModel.EngineStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = " ",
            fontSize = 48.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Welcome to DocDroid",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (status) {
                is ChatViewModel.EngineStatus.Loading -> "Loading the Needle model..."
                is ChatViewModel.EngineStatus.Ready ->
                    "Ask me to merge PDFs, extract text, rotate pages, and more.\n\n" +
                    "Try:\n" +
                    "• \"Merge report.pdf and appendix.pdf\"\n" +
                    "• \"Extract text from contract.pdf\"\n" +
                    "• \"Add CONFIDENTIAL watermark to every page\"\n" +
                    "• \"Convert these images to a PDF\""
                is ChatViewModel.EngineStatus.Error ->
                    "Error: ${status.message}\n\nMake sure the model files are in assets/needle/"
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 22.sp
        )
    }
}
