package com.ai.assistance.operit.ui.features.agreement.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 用户协议与隐私政策（单文档流）
 *
 * 与预览 08 屏一致：顶部返回 + 单行标题、正文五节、落款、底部固定主色按钮。
 * 不再出现「人话版 / 严谨版」双版本与开源客户端措辞。
 */
@Composable
fun AgreementScreen(onAgreementAccepted: () -> Unit, onBack: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var isButtonEnabled by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        repeat(5) {
            delay(1000)
            remainingSeconds--
        }
        isButtonEnabled = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
    ) {
        // 顶栏：返回箭头 + 标题
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center
        ) {
            if (onBack != null) {
                Text(
                    text = "←",
                    fontSize = 22.sp,
                    color = cs.onSurface,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { onBack() }
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }
            Text(
                text = "用户协议与隐私政策",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
        }
        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))

        // 正文
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            DocSection("一、服务说明")
            DocParagraph(
                "霖川 AI 是一款运行在用户设备本地的智能助手应用，由霖川网络提供。" +
                    "应用通过你配置的模型服务商处理指令，协助完成设备操作、文件管理与工作流编排。"
            )

            DocSection("二、账号与云同步")
            DocBullet("云同步为可选功能。未登录时，全部数据仅保存在本机。")
            DocBullet("登录后，配置与对话记录经客户端加密后上传；密钥由你的密码派生，服务端不持有明文密钥，无法解密。")
            DocBullet("请妥善保管同步密码，密码遗失将导致云端数据无法恢复。")

            DocSection("三、数据与隐私")
            DocParagraph(
                "我们不会将你的对话内容用于模型训练，也不会向第三方出售数据。" +
                    "应用仅在实现功能所需的范围内申请系统权限，你可随时在系统设置中撤销。"
            )

            DocSection("四、使用规范")
            DocParagraph(
                "你应对通过本应用执行的自动化操作负责。请勿将本应用用于侵犯他人权益、" +
                    "规避系统安全机制或其他违法违规用途。"
            )

            DocSection("五、服务变更")
            DocParagraph(
                "我们可能对功能与条款进行调整，重大变更将在应用内提示。继续使用即视为接受更新后的条款。"
            )

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "霖川网络 · 更新于 2026 年 10 月",
                fontSize = 10.5.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 底部固定按钮
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surface)
                .padding(horizontal = 22.dp, vertical = 14.dp)
        ) {
            Button(
                onClick = onAgreementAccepted,
                enabled = isButtonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = Color.White,
                    disabledContainerColor = cs.outlineVariant,
                    disabledContentColor = cs.onSurfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = if (isButtonEnabled) "我已阅读并同意" else "请稍候…（$remainingSeconds）",
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DocSection(title: String) {
    Text(
        text = title,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 18.dp, bottom = 7.dp)
    )
}

@Composable
private fun DocParagraph(text: String) {
    Text(
        text = text,
        fontSize = 12.5.sp,
        lineHeight = 21.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DocBullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = "·",
            fontSize = 12.5.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = text,
            fontSize = 12.5.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
