package com.docdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docdroid.model.ChatMessage
import com.docdroid.model.Role
import com.docdroid.ui.theme.*

@Composable
fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == Role.USER

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(if (isUser) UserBubble else AssistantBubble)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (isUser) Color.White else OnBackground,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        if (message.toolCalls.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            message.toolCalls.forEach { tc ->
                ToolExecutionCard(
                    toolName = tc.toolName,
                    status = tc.status.name,
                    result = tc.result,
                    error = tc.error
                )
            }
        }

        Text(
            text = formatTimestamp(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = ThinkingColor,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}
