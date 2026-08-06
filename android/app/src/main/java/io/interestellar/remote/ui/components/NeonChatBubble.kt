package io.interestellar.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.interestellar.remote.ui.ChatLine
import io.interestellar.remote.ui.theme.*

@Composable
fun NeonChatBubble(line: ChatLine) {
    val isUser = line.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            Box(
                Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .size(30.dp)
                    .background(ChatAgentIconBackground, RoundedCornerShape(10.dp))
                    .border(1.dp, NeonCyan.copy(alpha = .75f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AutoAwesome, null, Modifier.size(17.dp), tint = NeonCyan)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) .86f else .94f)
                .background(
                    brush = if (isUser) {
                        Brush.linearGradient(listOf(ChatBubbleUserGradientStart, ChatBubbleUserGradientEnd))
                    } else {
                        Brush.linearGradient(listOf(ChatBubbleAgentGradientStart, ChatBubbleAgentGradientEnd))
                    },
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 18.dp else 5.dp,
                        topEnd = if (isUser) 5.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp,
                    ),
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) NeonViolet.copy(alpha = .65f) else NeonCyan.copy(alpha = .48f),
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 18.dp else 5.dp,
                        topEnd = if (isUser) 5.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = 18.dp,
                    ),
                )
                .padding(horizontal = 15.dp, vertical = 13.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (isUser) ChatUserDot else NeonGreen, RoundedCornerShape(50))
                    )
                    Text(
                        if (isUser) "VOCÊ" else "ANTIGRAVITY",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isUser) ChatUserLabel else NeonCyan,
                    )
                }
                if (isUser) {
                    Text(line.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                } else {
                    FormattedMessage(line.text)
                }
            }
        }
    }
}

