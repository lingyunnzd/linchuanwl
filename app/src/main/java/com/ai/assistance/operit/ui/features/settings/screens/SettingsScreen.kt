package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.sync.AuthState
import com.ai.assistance.operit.sync.SyncManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navigateToUserPreferences: () -> Unit,
        navigateToGitHubAccount: () -> Unit,
        navigateToCloudAuth: () -> Unit,
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToGlobalDisplaySettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToChatBackupSettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit,
        navigateToSpeechServicesSettings: () -> Unit,
        navigateToExternalHttpChatSettings: () -> Unit,
        navigateToPersonaCardGeneration: () -> Unit,
        navigateToWaifuModeSettings: () -> Unit,
        navigateToTokenUsageStatistics: () -> Unit,
        navigateToPerformanceMonitor: () -> Unit,
        navigateToContextSummarySettings: () -> Unit,
        navigateToLayoutAdjustmentSettings: () -> Unit
) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var showClearCookieConfirm by remember { mutableStateOf(false) }
        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 2.dp, vertical = 4.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 账号与云服务 =======
                CloudSyncSection(navigateToCloudAuth = navigateToCloudAuth)

                // ======= 模型与服务商 =======
                SettingsSection(title = "模型与服务商") {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_model_parameters),
                                subtitle = stringResource(id = R.string.settings_model_params_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig,
                                showDivider = false
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_functional_model),
                                subtitle = stringResource(id = R.string.settings_functional_model_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToFunctionalConfig
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_speech_services),
                                subtitle = stringResource(id = R.string.settings_speech_services_subtitle),
                                icon = Icons.Default.RecordVoiceOver,
                                onClick = navigateToSpeechServicesSettings
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_prompt_title),
                                subtitle = stringResource(id = R.string.settings_system_prompts_subtitle),
                                icon = Icons.Default.ChatBubble,
                                onClick = navigateToModelPrompts
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_section_context_summary),
                                subtitle = stringResource(id = R.string.settings_context_summary_subtitle),
                                icon = Icons.Default.Analytics,
                                onClick = navigateToContextSummarySettings
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.persona_card_generation),
                                subtitle = stringResource(R.string.persona_card_generation_desc),
                                icon = Icons.Default.Face,
                                onClick = navigateToPersonaCardGeneration
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.waifu_mode_settings),
                                subtitle = stringResource(R.string.waifu_mode_settings_desc),
                                icon = Icons.Default.EmojiEmotions,
                                onClick = navigateToWaifuModeSettings
                        )
                }

                // ======= 界面与外观 =======
                SettingsSection(title = "界面与外观") {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_theme_appearance),
                                subtitle = stringResource(id = R.string.settings_theme_subtitle),
                                icon = Icons.Default.Palette,
                                onClick = navigateToThemeSettings,
                                showDivider = false
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.layout_adjustment),
                                subtitle = stringResource(R.string.layout_adjustment_subtitle),
                                icon = Icons.Default.AspectRatio,
                                onClick = navigateToLayoutAdjustmentSettings
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_global_display),
                                subtitle = stringResource(R.string.settings_global_display_subtitle),
                                icon = Icons.Default.Visibility,
                                onClick = navigateToGlobalDisplaySettings
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_user_preferences),
                                subtitle = stringResource(id = R.string.settings_user_preferences_subtitle),
                                icon = Icons.Default.Face,
                                onClick = navigateToUserPreferences
                        )
                        CompactSettingsItem(
                                title = stringResource(R.string.language_settings),
                                subtitle = stringResource(id = R.string.settings_language_subtitle),
                                icon = Icons.Default.Language,
                                onClick = navigateToLanguageSettings
                        )
                }

                // ======= 安全与数据 =======
                SettingsSection(title = "安全与数据") {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_tool_permissions),
                                subtitle = stringResource(id = R.string.settings_tool_permissions_subtitle),
                                icon = Icons.Default.AdminPanelSettings,
                                onClick = navigateToToolPermissions,
                                showDivider = false
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_data_backup),
                                subtitle = stringResource(id = R.string.settings_data_backup_desc),
                                icon = Icons.Default.CloudUpload,
                                onClick = navigateToChatBackupSettings
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_chat_history_management),
                                subtitle = stringResource(id = R.string.settings_chat_history_management_subtitle),
                                icon = Icons.Default.ManageHistory,
                                onClick = navigateToChatHistorySettings
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_token_usage_stats),
                                subtitle = stringResource(id = R.string.settings_token_usage_subtitle),
                                icon = Icons.Default.Analytics,
                                onClick = navigateToTokenUsageStatistics
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_performance_monitor),
                                subtitle = stringResource(id = R.string.settings_performance_monitor_subtitle),
                                icon = Icons.Default.Speed,
                                onClick = navigateToPerformanceMonitor
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_external_http_chat),
                                subtitle = stringResource(id = R.string.settings_external_http_chat_subtitle),
                                icon = Icons.Default.SettingsEthernet,
                                onClick = navigateToExternalHttpChatSettings
                        )
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_clear_cookies),
                                subtitle = stringResource(id = R.string.settings_clear_cookies_subtitle),
                                icon = Icons.Default.DeleteSweep,
                                onClick = { showClearCookieConfirm = true }
                        )
                }

                Spacer(modifier = Modifier.height(12.dp))
        }

        if (showClearCookieConfirm) {
                AlertDialog(
                        onDismissRequest = { showClearCookieConfirm = false },
                        title = { Text(stringResource(R.string.clear_cookies_dialog_title)) },
                        text = { Text(stringResource(R.string.clear_cookies_dialog_message)) },
                        confirmButton = {
                                TextButton(
                                        onClick = {
                                                showClearCookieConfirm = false
                                                scope.launch {
                                                        try {
                                                                CookiePrivacyManager.clearAllCookies()
                                                                Toast.makeText(
                                                                        context,
                                                                        context.getString(R.string.clear_cookies_success),
                                                                        Toast.LENGTH_SHORT
                                                                ).show()
                                                        } catch (e: Exception) {
                                                                AppLogger.e("SettingsScreen", "Failed to clear cookies", e)
                                                                Toast.makeText(
                                                                        context,
                                                                        context.getString(R.string.clear_cookies_failed),
                                                                        Toast.LENGTH_SHORT
                                                                ).show()
                                                        }
                                                }
                                        }
                                ) {
                                        Text(stringResource(R.string.clear_cookies_confirm))
                                }
                        },
                        dismissButton = {
                                TextButton(onClick = { showClearCookieConfirm = false }) {
                                        Text(stringResource(android.R.string.cancel))
                                }
                        }
                )
        }
}
@Composable
private fun SettingsSection(
        title: String,
        content: @Composable ColumnScope.() -> Unit
) {
        val cs = MaterialTheme.colorScheme
        Column(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 18.dp)
        ) {
                Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
                )
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = cs.surface,
                        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.6f))
                ) {
                        Column(content = content)
                }
        }
}

@Composable
private fun CompactSettingsItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit,
        showDivider: Boolean = true,
        trailingText: String? = null
) {
        val cs = MaterialTheme.colorScheme
        Column(modifier = Modifier.fillMaxWidth()) {
                if (showDivider) {
                        HorizontalDivider(
                                modifier = Modifier.padding(start = 58.dp),
                                thickness = 0.6.dp,
                                color = cs.outlineVariant.copy(alpha = 0.45f)
                        )
                }
                Row(
                        modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(cs.primary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                        ) {
                                Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = cs.primary,
                                        modifier = Modifier.size(18.dp)
                                )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = cs.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                        text = subtitle,
                                        fontSize = 11.5.sp,
                                        color = cs.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                )
                        }
                        if (trailingText != null) {
                                Text(
                                        text = trailingText,
                                        fontSize = 11.5.sp,
                                        color = cs.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                )
                        }
                        Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = cs.outline,
                                modifier = Modifier.padding(start = 6.dp).size(16.dp)
                        )
                }
        }
}

// ==================== 霖川云服务 ====================

/** 霖川云服务区块：账号登录/注册 + 配置与话题的端到端加密同步 */
@Composable
private fun CloudSyncSection(navigateToCloudAuth: () -> Unit) {
        // 同步模块初始化失败时（极少数情况）不显示入口，避免崩溃
        val syncReady = remember { runCatching { SyncManager.isInitialized() }.getOrDefault(false) }
        if (!syncReady) return

        val authState by SyncManager.state.collectAsState()
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }

        SettingsSection(title = "账号与云服务") {
                when (val state = authState) {
                        is AuthState.LoggedIn -> {
                                CompactSettingsItem(
                                        title = "霖川账号",
                                        subtitle = state.username,
                                        icon = Icons.Default.CloudDone,
                                        onClick = {},
                                        showDivider = false,
                                        trailingText = "已登录"
                                )
                                CompactSettingsItem(
                                        title = if (busy) "同步中…" else "云同步",
                                        subtitle = statusText ?: "AES-256-GCM 端侧加密，服务端不可解密",
                                        icon = Icons.Default.Sync,
                                        onClick = {
                                                if (!busy) {
                                                busy = true
                                                statusText = null
                                                scope.launch {
                                                        val errors = mutableListOf<String>()
                                                        var pulledCount = 0
                                                        SyncManager.pushSettings()
                                                                .onFailure { errors.add("配置上传失败") }
                                                        SyncManager.pullSettings()
                                                                .onFailure { errors.add("配置拉取失败") }
                                                        SyncManager.pullTopics()
                                                                .onSuccess { pulledCount = it }
                                                                .onFailure { errors.add("话题拉取失败") }
                                                        SyncManager.pushTopics()
                                                                .onFailure { errors.add("话题上传失败") }
                                                        statusText = if (errors.isEmpty()) {
                                                                if (pulledCount > 0) "同步完成，拉取 $pulledCount 条话题更新"
                                                                else "同步完成，已是最新"
                                                        } else {
                                                                errors.distinct().joinToString("，")
                                                        }
                                                        busy = false
                                                }
                                                }
                                        }
                                )
                                CompactSettingsItem(
                                        title = "退出登录",
                                        subtitle = "停止云端同步，本地数据完全保留",
                                        icon = Icons.Default.Logout,
                                        onClick = {
                                                SyncManager.logout()
                                                statusText = null
                                        }
                                )
                        }
                        AuthState.LoggedOut -> {
                                CompactSettingsItem(
                                        title = "霖川账号",
                                        subtitle = "登录后自动加密同步配置与话题",
                                        icon = Icons.Default.CloudUpload,
                                        onClick = navigateToCloudAuth,
                                        showDivider = false,
                                        trailingText = "未登录"
                                )
                        }
                }
        }
}
