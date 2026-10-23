package com.ai.assistance.operit.ui.features.cloudauth

import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.sync.AuthState
import com.ai.assistance.operit.sync.SyncManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

/**
 * 霖川云服务 · 登录/注册全屏页
 * - 邮箱作为主标识（username 自动取邮箱前缀）
 * - 注册：邮箱 + 密码 + 确认密码
 * - 登录：邮箱 + 密码
 * - 成功后自动派生加密密钥并同步首次数据 / 拉取云端服务商模板
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAuthScreen(onGoBack: () -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val authState by SyncManager.state.collectAsState()

        LaunchedEffect(authState) {
                if (authState is AuthState.LoggedIn) {
                        // 已登录用户从外部导航进来 → 直接返回上一页
                        onGoBack()
                }
        }

        var mode by remember { mutableStateOf(AuthMode.LOGIN) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var errorText by remember { mutableStateOf<String?>(null) }
        var successText by remember { mutableStateOf<String?>(null) }

        val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        val passwordValid = password.length >= 6
        val confirmValid = confirmPassword.isEmpty() || confirmPassword == password

        Scaffold(
                topBar = {
                        TopAppBar(
                                title = { Text("霖川云服务") },
                                navigationIcon = {
                                        IconButton(onClick = { if (!busy) onGoBack() }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                        }
                                }
                        )
                }
        ) { padding ->
                Box(
                        modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        contentAlignment = Alignment.Center
                ) {
                        Card(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                        ) {
                                Column(
                                        modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Cloud,
                                                contentDescription = null,
                                                modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                                text = if (mode == AuthMode.LOGIN) "登录霖川账号" else "注册霖川账号",
                                                style = MaterialTheme.typography.headlineSmall,
                                                modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                                text = "数据经 AES-256-GCM 端到端加密后同步到霖川云端，密码用于派生密钥",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 邮箱
                                        OutlinedTextField(
                                                value = email,
                                                onValueChange = {
                                                        email = it
                                                        errorText = null
                                                },
                                                label = { Text("邮箱") },
                                                singleLine = true,
                                                enabled = !busy,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                                modifier = Modifier.fillMaxWidth()
                                        )

                                        // 密码
                                        OutlinedTextField(
                                                value = password,
                                                onValueChange = {
                                                        password = it
                                                        errorText = null
                                                },
                                                label = { Text("密码（至少 6 位）") },
                                                singleLine = true,
                                                enabled = !busy,
                                                visualTransformation = PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                modifier = Modifier.fillMaxWidth()
                                        )

                                        // 注册：确认密码
                                        if (mode == AuthMode.REGISTER) {
                                                OutlinedTextField(
                                                        value = confirmPassword,
                                                        onValueChange = {
                                                                confirmPassword = it
                                                                errorText = null
                                                        },
                                                        label = { Text("确认密码") },
                                                        singleLine = true,
                                                        enabled = !busy,
                                                        visualTransformation = PasswordVisualTransformation(),
                                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                        modifier = Modifier.fillMaxWidth()
                                                )
                                        }

                                        // 错误/成功提示
                                        if (errorText != null) {
                                                Text(
                                                        text = errorText!!,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }
                                        if (successText != null) {
                                                Text(
                                                        text = successText!!,
                                                        color = Color(0xFF1E8E3E),
                                                        style = MaterialTheme.typography.bodySmall
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 提交按钮
                                        Button(
                                                onClick = {
                                                        busy = true
                                                        errorText = null
                                                        successText = null
                                                        val cleanEmail = email.trim().lowercase()
                                                        val cleanPwd = password

                                                        scope.launch {
                                                                val result = if (mode == AuthMode.REGISTER) {
                                                                        if (!confirmValid) {
                                                                                errorText = "两次密码不一致"
                                                                                busy = false
                                                                                return@launch
                                                                        }
                                                                        // username 取邮箱 @ 前部分，后端同时存完整 email
                                                                        val username = cleanEmail.substringBefore("@")
                                                                        SyncManager.register(username, cleanEmail, cleanPwd)
                                                                } else {
                                                                        val username = cleanEmail.substringBefore("@")
                                                                        SyncManager.login(username, cleanPwd)
                                                                }

                                                                result.fold(
                                                                        onSuccess = { user ->
                                                                                var providerMsg = ""
                                                                                runCatching {
                                                                                        SyncManager.deriveKey(cleanPwd)
                                                                                        if (mode == AuthMode.REGISTER) {
                                                                                                SyncManager.pushSettings()
                                                                                                SyncManager.pushTopics()
                                                                                        } else {
                                                                                                SyncManager.pullSettings()
                                                                                                SyncManager.pullTopics()
                                                                                        }
                                                                                        SyncManager.pullProviderTemplatesAndImport()
                                                                                                .onSuccess { n -> if (n > 0) providerMsg = "，已预装 $n 个云端服务商" }
                                                                                                .onFailure { AppLogger.e("CloudSync", "拉服务商模板失败", it) }
                                                                                }.onFailure { AppLogger.e("CloudSync", "首次同步失败", it) }

                                                                                successText = "欢迎，${user.username}${providerMsg}，正在返回..."
                                                                                busy = false
                                                                                // 等一下让用户看到成功提示
                                                                                kotlinx.coroutines.delay(900)
                                                                                onGoBack()
                                                                        },
                                                                        onFailure = { e ->
                                                                                errorText = e.message?.let { shortenBackendError(it) }
                                                                                        ?: "操作失败，请检查网络"
                                                                                busy = false
                                                                        }
                                                                )
                                                        }
                                                },
                                                enabled = !busy && emailValid && passwordValid && (mode != AuthMode.REGISTER || confirmValid),
                                                modifier = Modifier.fillMaxWidth()
                                        ) {
                                                if (busy) {
                                                        CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                }
                                                Text(if (busy) "请稍候…" else if (mode == AuthMode.REGISTER) "注册" else "登录")
                                        }

                                        // 切换模式
                                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                                TextButton(
                                                        onClick = {
                                                                mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                                                                errorText = null
                                                                successText = null
                                                        },
                                                        enabled = !busy
                                                ) {
                                                        Text(if (mode == AuthMode.LOGIN) "没有账号？去注册" else "已有账号？去登录")
                                                }
                                        }
                                }
                        }
                }
        }
}

private enum class AuthMode { LOGIN, REGISTER }

/** 把后端英文错误译成用户可读的中文 */
private fun shortenBackendError(raw: String): String {
        val lower = raw.lowercase()
        return when {
                lower.contains("network") || lower.contains("connect") -> "网络连接失败，请检查网络后重试"
                lower.contains("timeout") -> "请求超时，请稍后重试"
                lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid password") -> "邮箱或密码错误"
                lower.contains("404") || lower.contains("not found") -> "账号不存在，请先注册"
                lower.contains("already exists") || lower.contains("duplicate") -> "该邮箱已注册，请直接登录"
                lower.contains("rate limit") || lower.contains("too many") -> "操作频繁，请稍后再试"
                lower.contains("server") -> "服务器繁忙，请稍后重试"
                raw.length > 80 -> raw.take(80) + "…"
                else -> raw
        }
}
