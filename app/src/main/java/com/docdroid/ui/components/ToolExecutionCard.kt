package com.docdroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docdroid.ui.theme.*

@Composable
fun ToolExecutionCard(
    toolName: String,
    status: String,
    result: String = "",
    error: String? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = when (status) {
        "SUCCESS" -> ToolCardBg
        "FAILED" -> Color(0xFFFFEBEE)
        else -> Color(0xFFFFF8E1)
    }
    val icon = when (status) {
        "SUCCESS" -> Icons.Default.CheckCircle
        "FAILED" -> Icons.Default.Error
        else -> Icons.Default.HourglassBottom
    }
    val iconColor = when (status) {
        "SUCCESS" -> Success
        "FAILED" -> Error
        else -> ThinkingColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = toolName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = status,
                    fontSize = 10.sp,
                    color = iconColor
                )
            }
            if (result.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.take(300),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = OnBackground
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = error.take(200),
                    fontSize = 11.sp,
                    color = Error
                )
            }
        }
    }
}
