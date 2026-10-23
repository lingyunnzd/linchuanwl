package com.ai.assistance.operit.ui.features.announcement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 霖川公告弹窗
 *
 * level: info（蓝）/ warning（橙）/ critical（红），控制头部图标配色。
 * 按钮：稍后查看（弱化）+ 我知道了（主色）。
 */
@Composable
fun RemoteAnnouncementDialog(
    title: String,
    body: String,
    acknowledgeText: String = "我知道了",
    level: String = "info",
    onAcknowledge: () -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme

    val (levelColor, levelBg, levelIcon): Triple<Color, Color, ImageVector> = when (level) {
        "warning" -> Triple(Color(0xFFD48806), Color(0xFFFFF4E0), Icons.Default.Warning)
        "critical" -> Triple(Color(0xFFE0483D), Color(0xFFFBE9E7), Icons.Default.Error)
        else -> Triple(cs.primary, cs.primary.copy(alpha = 0.10f), Icons.Default.Info)
    }

    Dialog(
        onDismissRequest = { onDismiss?.invoke() ?: onAcknowledge() },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cs.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 头部
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(levelBg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = levelIcon,
                        contentDescription = null,
                        tint = levelColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // 内容
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = cs.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (onDismiss != null) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("稍后查看", color = cs.onSurfaceVariant) }
                }
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = cs.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = acknowledgeText, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
