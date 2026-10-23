package com.ai.assistance.operit.ui.features.cloudauth

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

private enum class VerifyState { IDLE, SUCCESS, FAIL }
private enum class AuthMode { REGISTER, LOGIN }
private enum class Step { EMAIL, CODE, PASSWORD }

/**
 * 霖川云服务 · 登录 / 注册
 *
 * 视觉规范：纯色底、单层标题栏（外层导航已提供返回）、字段聚焦描边、分步横向滑动。
 * 验证码：Telegram 风格方格，输入跳跃、正确变绿、错误变红、成功画圆+勾。
 * 流程：邮箱 → 邮箱验证码 → 密码（注册时含确认密码）。
 */
@Composable
fun CloudAuthScreen(onGoBack: () -> Unit, onSkip: (() -> Unit)? = null) {
    val scope = rememberCoroutineScope()
    val authState by SyncManager.state.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) onGoBack()
    }

    var mode by remember { mutableStateOf(AuthMode.REGISTER) }
    var step by remember { mutableStateOf(Step.EMAIL) }
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var code by remember { mutableStateOf("") }
    var codeFocused by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }
    var verificationSupported by remember { mutableStateOf(true) }
    var codeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    var verifyState by remember { mutableStateOf(VerifyState.IDLE) }
    var jumpIndex by remember { mutableIntStateOf(-1) }

    val cs = MaterialTheme.colorScheme
    val emailValid = Patterns.EMAIL_ADDRESS.matcher(email.text.trim()).matches()
    val passwordValid = password.length >= 6
    val confirmValid = confirmPassword.isEmpty() || confirmPassword == password

    val nextEnabled = when (step) {
        Step.EMAIL -> emailValid
        Step.CODE -> code.length == 6 || !verificationSupported
        Step.PASSWORD -> passwordValid && (mode != AuthMode.REGISTER || confirmValid)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // 步骤回退（页面级返回由外层导航提供，避免双层返回栏）
        Box(modifier = Modifier.height(34.dp), contentAlignment = Alignment.CenterStart) {
            if (step != Step.EMAIL) {
                Text(
                    text = "← 上一步",
                    color = cs.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            if (busy) return@clickable
                            errorText = null
                            step = when (step) {
                                Step.PASSWORD -> if (codeSent) Step.CODE else Step.EMAIL
                                Step.CODE -> Step.EMAIL
                                Step.EMAIL -> Step.EMAIL
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp)
                )
            }
        }

        // 品牌标识
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            BrandMark(size = 64)
        }
        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = when (step) {
                Step.EMAIL -> if (mode == AuthMode.REGISTER) "注册霖川账号" else "登录霖川账号"
                Step.CODE -> "输入验证码"
                Step.PASSWORD -> if (mode == AuthMode.REGISTER) "设置同步密码" else "输入密码"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = when (step) {
                Step.EMAIL ->
                    if (mode == AuthMode.REGISTER) "使用邮箱注册，配置与对话记录将在设备之间加密同步。"
                    else "使用已注册邮箱登录，继续同步你的配置与对话。"
                Step.CODE -> "6 位验证码已发送至 ${email.text.trim().lowercase()}"
                Step.PASSWORD -> "密码用于派生加密密钥，服务端全程无法解密你的数据。"
            },
            fontSize = 12.5.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(22.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val dir = if (forward) AnimatedContentTransitionScope.SlideDirection.Left
                else AnimatedContentTransitionScope.SlideDirection.Right
                slideIntoContainer(towards = dir, animationSpec = tween(320, easing = EaseInOutCubic))
                    .togetherWith(
                        slideOutOfContainer(towards = dir, animationSpec = tween(320, easing = EaseInOutCubic))
                    )
            },
            label = "auth-steps"
        ) { currentStep ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (currentStep) {
                    Step.EMAIL -> {
                        FieldLabel("邮箱地址")
                        BrandTextField(
                            value = email.text,
                            onValueChange = { email = TextFieldValue(it.trim().lowercase()); errorText = null },
                            placeholder = "you@example.com",
                            keyboardType = KeyboardType.Email,
                            enabled = !busy
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        HelperText("该邮箱将作为账号，验证码会发送到这里。")
                    }
                    Step.CODE -> {
                        FieldLabel("验证码")
                        CodeCells(
                            code = code,
                            focused = codeFocused,
                            verifyState = verifyState,
                            jumpIndex = jumpIndex,
                            onCodeChange = { newCode ->
                                if (verifyState != VerifyState.IDLE) return@CodeCells
                                val prevLen = code.length
                                code = newCode
                                errorText = null
                                if (newCode.length > prevLen && newCode.length <= 6) {
                                    jumpIndex = newCode.length - 1
                                }
                            },
                            onFocusChange = { codeFocused = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (countdown > 0) "重新发送需等待 $countdown 秒" else "未收到？可重新发送",
                                fontSize = 11.5.sp,
                                color = cs.onSurfaceVariant
                            )
                            Text(
                                text = "重新发送",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (countdown == 0) cs.primary else cs.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable(enabled = countdown == 0) {
                                    if (countdown != 0 || busy) return@clickable
                                    scope.launch {
                                        val cleanEmail = email.text.trim().lowercase()
                                        SyncManager.sendCode(
                                            cleanEmail,
                                            if (mode == AuthMode.REGISTER) "register" else "login"
                                        ).fold(
                                            onSuccess = {
                                                codeSent = true
                                                startCountdown { countdown = it; if (it == 0) countdownJob = null }
                                                countdown = 60
                                                errorText = null
                                            },
                                            onFailure = { errorText = "验证码发送失败，请稍后重试" }
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Step.PASSWORD -> {
                        FieldLabel("密码")
                        BrandTextField(
                            value = password,
                            onValueChange = { password = it; errorText = null },
                            placeholder = "至少 6 位",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !busy
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        HelperText("建议字母与数字组合，请牢记该密码。")
                        if (mode == AuthMode.REGISTER) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FieldLabel("确认密码")
                            BrandTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it; errorText = null },
                                placeholder = "再次输入密码",
                                keyboardType = KeyboardType.Password,
                                visualTransformation = PasswordVisualTransformation(),
                                enabled = !busy
                            )
                            if (!confirmValid) {
                                Spacer(modifier = Modifier.height(7.dp))
                                HelperText("两次输入的密码不一致", isError = true)
                            }
                        }
                    }
                }
            }
        }

        if (errorText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText!!,
                color = cs.error,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
        if (successText != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = successText!!, color = Color(0xFF2E6E63), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    errorText = null
                    val cleanEmail = email.text.trim().lowercase()
                    val username = cleanEmail.substringBefore("@")

                    when (step) {
                        Step.EMAIL -> {
                            SyncManager.sendCode(
                                cleanEmail,
                                if (mode == AuthMode.REGISTER) "register" else "login"
                            ).fold(
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
                            if (verificationSupported) {
                                SyncManager.verifyCode(
                                    cleanEmail,
                                    code,
                                    if (mode == AuthMode.REGISTER) "register" else "login"
                                ).fold(
                                    onSuccess = { res ->
                                        when (res) {
                                            is CodeVerifyResult.Ok -> {
                                                verifyState = VerifyState.SUCCESS
                                                delay(1100)
                                                step = Step.PASSWORD
                                                verifyState = VerifyState.IDLE
                                                code = ""
                                            }
                                            is CodeVerifyResult.NotSupported -> {
                                                verificationSupported = false
                                                step = Step.PASSWORD
                                            }
                                        }
                                    },
                                    onFailure = {
                                        verifyState = VerifyState.FAIL
                                        errorText = "验证码错误或已过期，请重试"
                                        delay(700)
                                        verifyState = VerifyState.IDLE
                                        code = ""
                                    }
                                )
                            } else {
                                step = Step.PASSWORD
                            }
                            busy = false
                        }
                        Step.PASSWORD -> {
                            val result = if (mode == AuthMode.REGISTER) {
                                if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                                    errorText = "两次密码不一致"; busy = false; return@launch
                                }
                                SyncManager.register(username, cleanEmail, password, code)
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
                                    delay(700)
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
                containerColor = cs.primary,
                contentColor = Color.White,
                disabledContainerColor = cs.outlineVariant,
                disabledContentColor = cs.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Text(
                    text = when (step) {
                        Step.EMAIL -> "继续"
                        Step.CODE -> "验证并继续"
                        Step.PASSWORD -> if (mode == AuthMode.REGISTER) "创建账号并登录" else "登录"
                    },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(
                onClick = {
                    if (busy) return@TextButton
                    mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN
                    step = Step.EMAIL
                    errorText = null
                    successText = null
                    code = ""
                    password = ""
                    confirmPassword = ""
                }
            ) {
                Text(
                    text = if (mode == AuthMode.LOGIN) "还没有账号？注册新账号" else "已有账号？直接登录",
                    color = cs.primary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (onSkip != null) {
            TextButton(
                onClick = { if (!busy) onSkip() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "暂不登录，先本地使用",
                    color = cs.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Text(
            text = if (mode == AuthMode.REGISTER) "注册即代表同意《用户协议》与《隐私政策》" else "登录后可在任意设备恢复你的配置",
            fontSize = 10.5.sp,
            color = cs.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// =============== 通用组件 ===============

@Composable
private fun BrandMark(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size((size * 0.52f).dp)) {
            val w = this.size.width
            val h = this.size.height
            val stroke = w * 0.14f
            drawLine(
                color = Color.White,
                start = Offset(w * 0.22f, h * 0.12f),
                end = Offset(w * 0.22f, h * 0.88f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.5f, h * 0.26f),
                end = Offset(w * 0.5f, h * 0.74f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.78f, h * 0.38f),
                end = Offset(w * 0.78f, h * 0.62f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun HelperText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BrandTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val cs = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        textStyle = TextStyle(color = cs.onSurface, fontSize = 14.5.sp),
        cursorBrush = SolidColor(cs.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) cs.primary else cs.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(cs.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = cs.onSurfaceVariant.copy(alpha = 0.65f),
                        fontSize = 14.5.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun CodeCells(
    code: String,
    focused: Boolean,
    verifyState: VerifyState,
    jumpIndex: Int,
    onCodeChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    // 输入法被系统手势/误触收起后焦点仍在，单纯 requestFocus 不会重新弹键盘，必须显式 show()
    val keyboardController = LocalSoftwareKeyboardController.current
    val transition = rememberInfiniteTransition(label = "code-caret")
    val caretAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "caret-alpha"
    )

    // 成功画圆进度
    val ringProgress by animateFloatAsState(
        targetValue = if (verifyState == VerifyState.SUCCESS) 1f else 0f,
        animationSpec = tween(450, easing = EaseInOutCubic),
        label = "ring-progress"
    )
    val checkProgress by animateFloatAsState(
        targetValue = if (verifyState == VerifyState.SUCCESS) 1f else 0f,
        animationSpec = tween(280, delayMillis = 380, easing = EaseInOutCubic),
        label = "check-progress"
    )
    // 失败红圈进度
    val failRingProgress by animateFloatAsState(
        targetValue = if (verifyState == VerifyState.FAIL) 1f else 0f,
        animationSpec = tween(450, easing = EaseInOutCubic),
        label = "fail-ring-progress"
    )
    val failXProgress by animateFloatAsState(
        targetValue = if (verifyState == VerifyState.FAIL) 1f else 0f,
        animationSpec = tween(280, delayMillis = 380, easing = EaseInOutCubic),
        label = "fail-x-progress"
    )
    // 失败抖动 — 用无限过渡驱动，确保重组
    val shakeTransition = rememberInfiniteTransition(label = "fail-shake")
    val shakeWave by shakeTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "shake-wave"
    )
    val shakeX = if (verifyState == VerifyState.FAIL) {
        (Math.sin(shakeWave * Math.PI * 4) * 6).toFloat()
    } else 0f

    val baseBorder = cs.outlineVariant
    val green = Color(0xFF2E9E5B)
    val red = Color(0xFFE0483D)
    val greenBg = Color(0xFFE6F4EC)
    val redBg = Color(0xFFFBE9E7)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(shakeX.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(6) { idx ->
                val digit = code.getOrNull(idx)?.toString() ?: ""
                val active = focused && code.length == idx && verifyState == VerifyState.IDLE
                val isJumping = jumpIndex == idx

                val jumpAnim by animateFloatAsState(
                    targetValue = if (isJumping) -10f else 0f,
                    animationSpec = tween(180, easing = EaseInOutCubic),
                    label = "jump-$idx"
                )

                val (borderColor, bgColor, textColor) = when (verifyState) {
                    VerifyState.SUCCESS -> Triple(green, greenBg, green)
                    VerifyState.FAIL -> Triple(red, redBg, red)
                    VerifyState.IDLE -> when {
                        active -> Triple(cs.primary, cs.surface, cs.onSurface)
                        digit.isNotEmpty() -> Triple(cs.primary.copy(alpha = 0.55f), cs.surface, cs.onSurface)
                        else -> Triple(baseBorder, cs.surface, cs.onSurface)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .offset { IntOffset(0, jumpAnim.roundToInt()) }
                        .border(
                            width = if (active || verifyState != VerifyState.IDLE) 1.5.dp else 1.dp,
                            color = borderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .background(bgColor, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (digit.isNotEmpty()) {
                        Text(
                            text = digit,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textColor
                        )
                    } else if (active) {
                        Box(
                            modifier = Modifier
                                .width(1.5.dp)
                                .height(22.dp)
                                .background(cs.primary.copy(alpha = caretAlpha))
                        )
                    }
                }
            }
        }

        // 成功画圆 + 勾（覆盖在格子上方）
        if (verifyState == VerifyState.SUCCESS) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    val stroke = 3.dp.toPx()
                    // 画圆
                    val radius = size.minDimension / 2 - stroke
                    val sweep = 360f * ringProgress
                    drawArc(
                        color = green,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // 画勾（两段线）
                    if (checkProgress > 0f) {
                        val p = checkProgress
                        // 勾的路径：从左下到中到右上
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val left = Offset(cx - radius * 0.5f, cy)
                        val mid = Offset(cx - radius * 0.08f, cy + radius * 0.38f)
                        val right = Offset(cx + radius * 0.55f, cy - radius * 0.35f)
                        val totalLen = (mid - left).getDistance() + (right - mid).getDistance()
                        val drawn = totalLen * p
                        // 第一段
                        val seg1Len = (mid - left).getDistance()
                        if (drawn <= seg1Len) {
                            val t = drawn / seg1Len
                            val end = Offset(
                                left.x + (mid.x - left.x) * t,
                                left.y + (mid.y - left.y) * t
                            )
                            drawLine(green, left, end, stroke, cap = StrokeCap.Round)
                        } else {
                            drawLine(green, left, mid, stroke, cap = StrokeCap.Round)
                            val seg2Drawn = drawn - seg1Len
                            val seg2Len = (right - mid).getDistance()
                            val t2 = (seg2Drawn / seg2Len).coerceIn(0f, 1f)
                            val end2 = Offset(
                                mid.x + (right.x - mid.x) * t2,
                                mid.y + (right.y - mid.y) * t2
                            )
                            drawLine(green, mid, end2, stroke, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }

        // 失败画红圈 + 红叉（覆盖在格子上方）
        if (verifyState == VerifyState.FAIL) {
            Box(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(52.dp)) {
                    val stroke = 3.dp.toPx()
                    val radius = size.minDimension / 2 - stroke
                    val sweep = 360f * failRingProgress
                    drawArc(
                        color = red,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    // 画叉（两段线，左上→右下，右上→左下）
                    if (failXProgress > 0f) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        val r = radius * 0.45f
                        val tl = Offset(cx - r, cy - r)
                        val br = Offset(cx + r, cy + r)
                        val tr = Offset(cx + r, cy - r)
                        val bl = Offset(cx - r, cy + r)
                        val segLen = (br - tl).getDistance()
                        val drawn = segLen * 2 * failXProgress
                        // 第一段：左上 → 右下
                        if (drawn <= segLen) {
                            val t = (drawn / segLen).coerceIn(0f, 1f)
                            val end = Offset(
                                tl.x + (br.x - tl.x) * t,
                                tl.y + (br.y - tl.y) * t
                            )
                            drawLine(red, tl, end, stroke, cap = StrokeCap.Round)
                        } else {
                            drawLine(red, tl, br, stroke, cap = StrokeCap.Round)
                            val seg2Drawn = drawn - segLen
                            val t2 = (seg2Drawn / segLen).coerceIn(0f, 1f)
                            val end2 = Offset(
                                tr.x + (bl.x - tr.x) * t2,
                                tr.y + (bl.y - tr.y) * t2
                            )
                            drawLine(red, tr, end2, stroke, cap = StrokeCap.Round)
                        }
                    }
                }
            }
        }

        BasicTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) onCodeChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier
                .matchParentSize()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = TextStyle(color = Color.Transparent)
        )

        // 点击整块区域即聚焦输入；同时强制唤起输入法，避免误触收起后无法再次触发
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // 等焦点真正落到输入框后再唤起键盘，避免部分机型首次请求无效
        delay(120)
        keyboardController?.show()
    }
}

// =============== Helpers ===============

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
        lower.contains("json") || lower.contains("quotation mark") || lower.contains("unexpected token") ->
            "服务返回数据异常，请更新到最新版本后重试"
        lower.contains("server") -> "服务器繁忙，请稍后重试"
        raw.length > 80 -> raw.take(80) + "…"
        else -> raw
    }
}
