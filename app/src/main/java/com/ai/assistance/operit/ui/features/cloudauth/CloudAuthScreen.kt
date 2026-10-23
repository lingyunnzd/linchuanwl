package com.ai.assistance.operit.ui.features.cloudauth

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.sync.AuthState
import com.ai.assistance.operit.sync.CodeSendResult
import com.ai.assistance.operit.sync.CodeVerifyResult
import com.ai.assistance.operit.sync.SyncManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 霖川云服务 · Telegram 风格登录/注册页
 *
 * 多步骤流程（AnimatedContent 滑动过渡）：
 *   步骤 1 — 邮箱（输入邮箱，自动取 @ 前缀当 username）
 *   步骤 2 — 验证码（6 格独立格子 + 翻转动画 + 60s 重发倒计时）
 *   步骤 3 — 密码（注册时有确认密码；登录时只有一个密码）
 *
 * 优雅降级：后端未实现 send-code/verify-code 端点（404）时自动跳过验证步骤。
 * 后端加端点后无需改 UI 代码即可生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudAuthScreen(onGoBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val authState by SyncManager.state.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) onGoBack()
    }

    var mode by remember { mutableStateOf(AuthMode.REGISTER) }
    var step by remember { mutableIntStateOf(Step.EMAIL) }
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var code by remember { mutableStateOf("") }
    var codeFocused by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var verificationSupported by remember { mutableStateOf(true) } // 后端是否支持验证码
    var codeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    val telegramBlue = Color(0xFF2AABEE)
    val telegramBlueDark = Color(0xFF229ED9)

    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.text.trim()).matches()
    val passwordValid = password.length >= 6
    val confirmValid = confirmPassword.isEmpty() || confirmPassword == password

    val nextEnabled = when (step) {
        Step.EMAIL -> emailValid
        Step.CODE -> code.length == 6 || !verificationSupported
        Step.PASSWORD -> passwordValid && (mode != AuthMode.REGISTER || confirmValid)
        else -> false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (step) {
                            Step.EMAIL -> if (mode == AuthMode.REGISTER) "注册霖川账号" else "登录霖川账号"
                            Step.CODE -> "输入验证码"
                            Step.PASSWORD -> if (mode == AuthMode.REGISTER) "设置密码" else "输入密码"
                            else -> ""
                        },
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step > Step.EMAIL) step-- else onGoBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = telegramBlue)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(telegramBlue, telegramBlueDark)))
                .padding(padding)
        ) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    val slideDir = if (forward) AnimatedContentTransitionScope.SlideDirection.Left
                    else AnimatedContentTransitionScope.SlideDirection.Right
                    slideIntoContainer(
                        towards = slideDir,
                        animationSpec = tween(300, easing = EaseInOutCubic)
                    ).togetherWith(
                        slideOutOfContainer(
                            towards = slideDir,
                            animationSpec = tween(300, easing = EaseInOutCubic)
                        )
                    )
                },
                contentAlignment = Alignment.Center,
                label = "auth-steps"
            ) { currentStep ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (currentStep) {
                        Step.EMAIL -> EmailStep(
                            mode = mode,
                            email = email.text,
                            onEmailChange = { email = TextFieldValue(it) },
                            onSwitchMode = {
                                mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                                errorText = null
                            },
                            busy = busy
                        )
                        Step.CODE -> CodeStep(
                            code = code,
                            focused = codeFocused,
                            onCodeChange = { code = it; errorText = null },
                            onFocusChange = { codeFocused = it },
                            countdown = countdown,
                            onResend = {
                                scope.launch {
                                    busy = true
                                    SyncManager.sendCode(email.text.trim().lowercase(), if (mode == AuthMode.REGISTER) "register" else "login")
                                        .fold(
                                            onSuccess = { res ->
                                                when (res) {
                                                    is CodeSendResult.Sent -> {
                                                        codeSent = true
                                                        startCountdown { countdown = it; if (it == 0) countdownJob = null }
                                                        countdown = 60
                                                        errorText = null
                                                    }
                                                    is CodeSendResult.NotSupported -> {
                                                        verificationSupported = false
                                                        step = Step.PASSWORD // 自动跳到密码步
                                                    }
                                                }
                                            },
                                            onFailure = { errorText = "验证码发送失败，请稍后重试" }
                                        )
                                    busy = false
                                }
                            }
                        )
                        Step.PASSWORD -> PasswordStep(
                            mode = mode,
                            password = password,
                            confirmPassword = confirmPassword,
                            onPasswordChange = { password = it },
                            onConfirmChange = { confirmPassword = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    // 错误/成功提示
                    if (errorText != null) {
                        Text(text = errorText!!, color = Color(0xFFFFDAD6), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (successText != null) {
                        Text(text = successText!!, color = Color(0xFFC8FFD4), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 主按钮
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                errorText = null
                                val cleanEmail = email.text.trim().lowercase()
                                val username = cleanEmail.substringBefore("@")

                                when (step) {
                                    Step.EMAIL -> {
                                        // 触发 send-code → 等结果后自动判断是否要跳 CODE 步还是 PASSWORD 步
                                        SyncManager.sendCode(cleanEmail, if (mode == AuthMode.REGISTER) "register" else "login")
                                            .fold(
                                                onSuccess = { res ->
                                                    when (res) {
                                                        is CodeSendResult.Sent -> {
                                                            codeSent = true
                                                            startCountdown { countdown = it; if (it == 0) countdownJob = null }
                                                            countdown = 60
                                                            step = Step.CODE
                                                        }
                                                        is CodeSendResult.NotSupported -> {
                                                            verificationSupported = false
                                                            step = Step.PASSWORD
                                                        }
                                                    }
                                                },
                                                onFailure = { errorText = shortenBackendError(it.message ?: "发送失败") }
                                            )
                                        busy = false
                                    }
                                    Step.CODE -> {
                                        // 验证验证码
                                        if (verificationSupported) {
                                            SyncManager.verifyCode(cleanEmail, code, if (mode == AuthMode.REGISTER) "register" else "login")
                                                .fold(
                                                    onSuccess = { res ->
                                                        when (res) {
                                                            is CodeVerifyResult.Ok -> step = Step.PASSWORD
                                                            is CodeVerifyResult.NotSupported -> {
                                                                verificationSupported = false
                                                                step = Step.PASSWORD
                                                            }
                                                        }
                                                    },
                                                    onFailure = { errorText = "验证码错误或已过期，请重试" }
                                                )
                                        } else {
                                            step = Step.PASSWORD
                                        }
                                        busy = false
                                    }
                                    Step.PASSWORD -> {
                                        // 提交注册/登录
                                        val result = if (mode == AuthMode.REGISTER) {
                                            if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                                                errorText = "两次密码不一致"; busy = false; return@launch
                                            }
                                            SyncManager.register(username, cleanEmail, password)
                                        } else {
                                            SyncManager.login(username, password)
                                        }
                                        result.fold(
                                            onSuccess = { user ->
                                                runCatching {
                                                    SyncManager.deriveKey(password)
                                                    if (mode == AuthMode.REGISTER) {
                                                        SyncManager.pushSettings()
                                                        SyncManager.pushTopics()
                                                    } else {
                                                        SyncManager.pullSettings()
                                                        SyncManager.pullTopics()
                                                    }
                                                    SyncManager.pullProviderTemplatesAndImport()
                                                        .onFailure { AppLogger.e("CloudSync", "拉服务商模板失败", it) }
                                                }.onFailure { AppLogger.e("CloudSync", "首次同步失败", it) }
                                                successText = "欢迎，${user.username}，正在进入..."
                                                kotlinx.coroutines.delay(700)
                                                onGoBack()
                                            },
                                            onFailure = { e ->
                                                errorText = shortenBackendError(e.message ?: "操作失败")
                                                busy = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !busy && nextEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = telegramBlue,
                            disabledContainerColor = Color.White.copy(alpha = 0.4f),
                            disabledContentColor = telegramBlue.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = telegramBlue)
                        } else {
                            Text(
                                text = when (currentStep) {
                                    Step.EMAIL -> "继续"
                                    Step.CODE -> "验证"
                                    Step.PASSWORD -> if (mode == AuthMode.REGISTER) "注册" else "登录"
                                    else -> "继续"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 底部提示
                    Text(
                        text = when (currentStep) {
                            Step.EMAIL -> if (mode == AuthMode.REGISTER) "注册即表示同意霖川网络服务条款与隐私政策" else "使用邮箱注册的账号登录"
                            Step.CODE -> if (countdown > 0) "验证码已发送到 $cleanEmail ($countdown s 后可重发)" else "验证码已发送到 $cleanEmail"
                            Step.PASSWORD -> "密码用于派生加密密钥，云端无法读取你的数据"
                            else -> ""
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// =============== 三个步骤 Composable ===============

@Composable
private fun EmailStep(
    mode: AuthMode,
    email: String,
    onEmailChange: (String) -> Unit,
    onSwitchMode: () -> Unit,
    busy: Boolean
) {
    val telegramBlue = Color(0xFF2AABEE)
    Text(
        text = "请输入邮箱地址",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "我们将发送一个验证码到你的邮箱",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))
    BasicTextField(
        value = email,
        onValueChange = { onEmailChange(it.trim().lowercase()) },
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
    Spacer(modifier = Modifier.height(20.dp))
    TextButton(onClick = onSwitchMode, enabled = !busy) {
        Text(
            text = if (mode == AuthMode.LOGIN) "没有账号？去注册" else "已有账号？去登录",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CodeStep(
    code: String,
    focused: Boolean,
    onCodeChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    countdown: Int,
    onResend: () -> Unit
) {
    val telegramBlue = Color(0xFF2AABEE)
    val focusRequester = remember { FocusRequester() }

    Text(
        text = "输入 6 位验证码",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))

    Box {
        // 6 个独立格子
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) { idx ->
                val digit = code.getOrNull(idx)?.toString() ?: ""
                val active = focused && code.length == idx && countdown != 0
                Box(
                    modifier = Modifier
                        .size(44.dp, 54.dp)
                        .border(
                            width = if (active) 2.dp else 1.5.dp,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) Color.White.copy(alpha = 0.25f)
                            else Color.White.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = digit,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (active) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(26.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }

        // 隐藏的 TextField 接收输入
        BasicTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChange(it) },
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(color = Color.Transparent)
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Spacer(modifier = Modifier.height(24.dp))

    TextButton(
        onClick = onResend,
        enabled = countdown == 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (countdown > 0) "$countdown 秒后可重发" else "重新发送验证码",
            color = if (countdown == 0) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.4f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun PasswordStep(
    mode: AuthMode,
    password: String,
    confirmPassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit
) {
    Text(
        text = if (mode == AuthMode.REGISTER) "设置你的密码" else "输入密码",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "至少 6 位",
        fontSize = 14.sp,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(32.dp))

    PasswordInput(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "密码"
    )

    if (mode == AuthMode.REGISTER) {
        Spacer(modifier = Modifier.height(14.dp))
        PasswordInput(
            value = confirmPassword,
            onValueChange = onConfirmChange,
            placeholder = "再次输入密码"
        )
    }
}

@Composable
private fun PasswordInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) { inner ->
        Box {
            if (value.isEmpty()) {
                Text(placeholder, color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
            }
            inner()
        }
    }
}

// =============== Helpers ===============

private enum class Step { EMAIL, CODE, PASSWORD }
private enum class AuthMode { REGISTER, LOGIN }

private fun startCountdown(onTick: (Int) -> Unit) {
    kotlinx.coroutines.GlobalScope.launch {
        repeat(60) { i ->
            onTick(60 - i)
            delay(1000)
        }
        onTick(0)
    }
}

private fun shortenBackendError(raw: String): String {
    val lower = raw.lowercase()
    return when {
        lower.contains("network") || lower.contains("connect") -> "网络连接失败，请检查网络"
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
