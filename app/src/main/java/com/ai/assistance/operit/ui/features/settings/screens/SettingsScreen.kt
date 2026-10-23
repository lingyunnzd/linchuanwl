package com.ai.assistance.operit.ui.features.settings.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.defaultTool.standard.CookiePrivacyManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.sync.AuthState
import com.ai.assistance.operit.sync.SyncManager
import com.ai.assistance.operit.ui.features.github.GitHubLoginDialog
import com.ai.assistance.operit.ui.theme.LocalThemePreferenceSnapshot
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        navigateToUserPreferences: () -> Unit,
        navigateToGitHubAccount: () -> Unit,
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
        val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
        val scope = rememberCoroutineScope()
        var showGitHubLogin by remember { mutableStateOf(false) }
        var showClearCookieConfirm by remember { mutableStateOf(false) }

        val isGitHubLoggedIn = githubAuth.isLoggedInFlow.collectAsState(initial = false).value
        val gitHubUser = githubAuth.userInfoFlow.collectAsState(initial = null).value
        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        val hasBackgroundImage = LocalThemePreferenceSnapshot.current.useBackgroundImage
        
        val cardContainerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surface
        } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 账号 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_account),
                        icon = Icons.Default.AccountCircle,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.github_account),
                                subtitle = if (isGitHubLoggedIn && gitHubUser != null) {
                                        "@${gitHubUser!!.login}"
                                } else {
                                        stringResource(R.string.github_account_not_logged_in)
                                },
                                icon = Icons.Default.Person,
                                onClick = navigateToGitHubAccount
                        )

                        if (isGitHubLoggedIn) {
                                CompactSettingsItem(
                                        title = stringResource(R.string.logout),
                                        subtitle = stringResource(R.string.github_account_logout_desc),
                                        icon = Icons.Default.Logout,
                                        onClick = {
                                                scope.launch { githubAuth.logout() }
                                        }
                                )
                        } else {
                                CompactSettingsItem(
                                        title = stringResource(R.string.login_github),
                                        subtitle = stringResource(R.string.github_account_login_desc),
                                        icon = Icons.Default.Login,
                                        onClick = { showGitHubLogin = true }
                                )
                        }
                }

                if (showGitHubLogin) {
                        GitHubLoginDialog(
                                onDismissRequest = { showGitHubLogin = false }
                        )
                }

                // ======= 霖川云服务 =======
                CloudSyncSection(cardContainerColor = cardContainerColor)

                // ======= 个性化配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person,
                        containerColor = cardContainerColor
                ) {
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
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_theme_appearance),
                                subtitle = stringResource(id = R.string.settings_theme_subtitle),
                                icon = Icons.Default.Palette,
                                onClick = navigateToThemeSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_global_display),
                                subtitle = stringResource(R.string.settings_global_display_subtitle),
                                icon = Icons.Default.Visibility,
                                onClick = navigateToGlobalDisplaySettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.layout_adjustment),
                                subtitle = stringResource(R.string.layout_adjustment_subtitle),
                                icon = Icons.Default.AspectRatio,
                                onClick = navigateToLayoutAdjustmentSettings
                        )
                }

                // ======= AI模型配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_model_parameters),
                                subtitle = stringResource(id = R.string.settings_model_params_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig
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
                        
                }

                // ======= 提示词配置 =======
                SettingsSection(
                        title = stringResource(R.string.settings_prompt_section),
                        icon = Icons.Default.Message,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_prompt_title),
                                subtitle = stringResource(id = R.string.settings_system_prompts_subtitle),
                                icon = Icons.Default.ChatBubble,
                                onClick = navigateToModelPrompts
                        )
                        
                        // 新增：人设卡生成
                        CompactSettingsItem(
                                title = stringResource(R.string.persona_card_generation),
                                subtitle = stringResource(R.string.persona_card_generation_desc),
                                icon = Icons.Default.Face,
                                onClick = navigateToPersonaCardGeneration
                        )
                        
                        // 新增：Waifu模式设置
                        CompactSettingsItem(
                                title = stringResource(R.string.waifu_mode_settings),
                                subtitle = stringResource(R.string.waifu_mode_settings_desc),
                                icon = Icons.Default.EmojiEmotions,
                                onClick = navigateToWaifuModeSettings
                        )
                }

                // ======= 上下文和总结设置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_context_summary),
                        icon = Icons.Default.Analytics,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_section_context_summary),
                                subtitle = stringResource(id = R.string.settings_context_summary_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToContextSummarySettings
                        )
                }

                // ======= 数据和权限 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_data_permissions),
                        icon = Icons.Default.Security,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_tool_permissions),
                                subtitle = stringResource(id = R.string.settings_tool_permissions_subtitle),
                                icon = Icons.Default.AdminPanelSettings,
                                onClick = navigateToToolPermissions
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
                }

                // ======= 隐私与数据清理 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_privacy_data_cleanup),
                        icon = Icons.Default.DeleteSweep,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_clear_cookies),
                                subtitle = stringResource(id = R.string.settings_clear_cookies_subtitle),
                                icon = Icons.Default.DeleteSweep,
                                onClick = { showClearCookieConfirm = true }
                        )
                }

                // ======= 外部调用 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_external_calls),
                        icon = Icons.Default.SettingsEthernet,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_external_http_chat),
                                subtitle = stringResource(id = R.string.settings_external_http_chat_subtitle),
                                icon = Icons.Default.SettingsEthernet,
                                onClick = navigateToExternalHttpChatSettings
                        )
                }

                // 底部间距
                Spacer(modifier = Modifier.height(16.dp))
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
        icon: ImageVector,
        containerColor: Color,
        content: @Composable ColumnScope.() -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // 分组标题
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )
                }
                
                // 内容区域
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                                containerColor = containerColor
                        )
                ) {
                        Column(
                                modifier = Modifier.padding(12.dp),
                                content = content
                        )
                }
        }
}

@Composable
private fun CompactSettingsItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClick() }
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                
                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                )
        }
}

// ==================== 霖川云服务 ====================

/** 霖川云服务区块：账号登录/注册 + 配置与话题的端到端加密同步 */
@Composable
private fun CloudSyncSection(cardContainerColor: Color) {
        // 同步模块初始化失败时（极少数情况）不显示入口，避免崩溃
        val syncReady = remember { runCatching { SyncManager.isInitialized() }.getOrDefault(false) }
        if (!syncReady) return

        val authState by SyncManager.state.collectAsState()
        val scope = rememberCoroutineScope()
        var showAuthDialog by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var statusText by remember { mutableStateOf<String?>(null) }

        SettingsSection(
                title = "霖川云服务",
                icon = Icons.Default.Cloud,
                containerColor = cardContainerColor
        ) {
                when (val state = authState) {
                        is AuthState.LoggedIn -> {
                                CompactSettingsItem(
                                        title = "已登录：${state.username}",
                                        subtitle = statusText ?: "数据经 AES-256-GCM 端到端加密后同步",
                                        icon = Icons.Default.CloudDone,
                                        onClick = {}
                                )
                                CompactSettingsItem(
                                        title = if (busy) "同步中…" else "立即同步",
                                        subtitle = "上传并拉取模型配置与聊天话题",
                                        icon = Icons.Default.Sync,
                                        onClick = {
                                                if (busy) return@CompactSettingsItem
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
                                        title = "登录 / 注册霖川账号",
                                        subtitle = statusText ?: "登录后自动加密同步配置与话题到霖川云端",
                                        icon = Icons.Default.CloudUpload,
                                        onClick = { showAuthDialog = true }
                                )
                        }
                }
        }

        if (showAuthDialog) {
                CloudAuthDialog(
                        onDismiss = { showAuthDialog = false },
                        onSuccess = { username ->
                                showAuthDialog = false
                                statusText = "欢迎，$username"
                        }
                )
        }
}

/** 登录/注册对话框：成功后自动派生加密密钥并做首次同步 */
@Composable
private fun CloudAuthDialog(onDismiss: () -> Unit, onSuccess: (String) -> Unit) {
        var isRegister by remember { mutableStateOf(false) }
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text(if (isRegister) "注册霖川账号" else "登录霖川账号") },
                text = {
                        Column {
                                OutlinedTextField(
                                        value = username,
                                        onValueChange = { username = it },
                                        label = { Text("用户名") },
                                        singleLine = true,
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                )
                                if (isRegister) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                                value = email,
                                                onValueChange = { email = it },
                                                label = { Text("邮箱") },
                                                singleLine = true,
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = { Text("密码") },
                                        singleLine = true,
                                        enabled = !busy,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth()
                                )
                                if (errorText != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                                text = errorText!!,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                        text = "密码用于派生加密密钥，云端无法读取你的数据",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(
                                        onClick = { isRegister = !isRegister; errorText = null },
                                        enabled = !busy,
                                        modifier = Modifier.align(Alignment.End)
                                ) {
                                        Text(if (isRegister) "已有账号？去登录" else "没有账号？去注册")
                                }
                        }
                },
                confirmButton = {
                        TextButton(
                                enabled = !busy && username.isNotBlank() && password.length >= 6 &&
                                        (!isRegister || email.contains("@")),
                                onClick = {
                                        busy = true
                                        errorText = null
                                        scope.launch {
                                                val result = if (isRegister) {
                                                        SyncManager.register(username.trim(), email.trim(), password)
                                                } else {
                                                        SyncManager.login(username.trim(), password)
                                                }
                                                result.fold(
                                                        onSuccess = { user ->
                                                                // 派生密钥 → 首次同步：注册推送本地，登录拉取云端
                                                                runCatching {
                                                                        SyncManager.deriveKey(password)
                                                                        if (isRegister) {
                                                                                SyncManager.pushSettings()
                                                                                SyncManager.pushTopics()
                                                                        } else {
                                                                                SyncManager.pullSettings()
                                                                                SyncManager.pullTopics()
                                                                        }
                                                                }.onFailure { AppLogger.e("CloudSync", "首次同步失败", it) }
                                                                onSuccess(user.username)
                                                        },
                                                        onFailure = { e ->
                                                                errorText = e.message ?: "操作失败，请检查网络"
                                                                busy = false
                                                        }
                                                )
                                        }
                                }
                        ) {
                                Text(if (busy) "请稍候…" else if (isRegister) "注册" else "登录")
                        }
                },
                dismissButton = {
                        TextButton(onClick = { if (!busy) onDismiss() }) { Text("取消") }
                }
        )
}
