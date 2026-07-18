package com.docdroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.docdroid.ui.theme.*

@Composable
fun MessageInput(
    onSendMessage: (String) -> Unit,
    onAttachFiles: () -> Unit,
    isLoading: Boolean = false,
    text: String = "",
    onTextChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = Surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onAttachFiles,
                enabled = !isLoading
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach files",
                    tint = Primary
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                placeholder = {
                    Text(
                        "Describe what you want to do...",
                        color = ThinkingColor
                    )
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color(0xFFFAFAFA),
                    unfocusedContainerColor = Color(0xFFFAFAFA)
                ),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(4.dp))

            FilledIconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text.trim())
                    }
                },
                enabled = text.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (text.isNotBlank() && !isLoading) Primary else Color(0xFFE0E0E0),
                    contentColor = if (text.isNotBlank() && !isLoading) Color.White else ThinkingColor
                ),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
