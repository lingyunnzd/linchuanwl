package com.ai.assistance.operit.ui.features.chat.components.style.input.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R

data class PendingQueueMessageItem(
    val id: Long,
    val text: String
)

@Composable
fun PendingMessageQueuePanel(
    queuedMessages: List<PendingQueueMessageItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDeleteMessage: (Long) -> Unit,
    onEditMessage: (Long) -> Unit,
    onSendMessage: (Long) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    itemColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier
) {
    if (queuedMessages.isEmpty()) return
    val colors = MaterialTheme.colorScheme
    val secondaryTint = colors.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_pending_queue_title, queuedMessages.size),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = secondaryTint,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 176.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = queuedMessages,
                key = { item -> item.id }
            ) { item ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(itemColor)
                            .border(
                                width = 1.dp,
                                color = colors.outlineVariant.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatListBulleted,
                        contentDescription = null,
                        tint = secondaryTint.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.text,
                        fontSize = 12.sp,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_pending_queue_item_state),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryTint,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.outlineVariant.copy(alpha = 0.35f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    QueueIconAction(
                        onClick = { onEditMessage(item.id) },
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = secondaryTint.copy(alpha = 0.85f),
                    )
                    QueueIconAction(
                        onClick = { onSendMessage(item.id) },
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.send),
                        tint = secondaryTint.copy(alpha = 0.85f),
                    )
                    QueueIconAction(
                        onClick = { onDeleteMessage(item.id) },
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = secondaryTint.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueIconAction(
    onClick: () -> Unit,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}
