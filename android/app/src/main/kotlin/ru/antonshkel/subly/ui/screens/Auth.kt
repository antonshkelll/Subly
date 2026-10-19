package ru.antonshkel.subly.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.ui.components.Divider
import ru.antonshkel.subly.ui.components.Group
import ru.antonshkel.subly.ui.components.PrimaryButton
import ru.antonshkel.subly.ui.components.Segmented
import ru.antonshkel.subly.ui.components.TextInput
import ru.antonshkel.subly.ui.theme.Subly

@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    serverUrl: String,
    onSubmit: (email: String, password: String, register: Boolean) -> Unit,
    onServer: (String) -> Unit,
    onPrivacy: () -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(1) }          // 0 — вход, 1 — регистрация
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverDialog by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val register = mode == 1
    val valid = email.contains('@') && password.length >= (if (register) 8 else 1)

    Column(
        Modifier.fillMaxSize().background(Subly.colors.background).statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(56.dp))
        AppMark(84)
        Spacer(Modifier.height(20.dp))
        Text("Subly", style = Subly.type.largeTitle, color = Subly.colors.label)
        Text("Все подписки — под контролем", style = Subly.type.body, color = Subly.colors.secondary)
        Spacer(Modifier.height(36.dp))

        Segmented(listOf("Вход", "Регистрация"), mode, { mode = it })
        Spacer(Modifier.height(16.dp))
        Group {
            TextInput(email, { email = it.trim() }, "Почта",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            Divider()
            TextInput(password, { password = it }, if (register) "Пароль — от 8 символов" else "Пароль",
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                actions = KeyboardActions(onDone = { focus.clearFocus(); if (valid && !busy) onSubmit(email, password, register) }),
                visual = PasswordVisualTransformation())
        }
        AnimatedVisibility(error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text(error.orEmpty(), style = Subly.type.footnote, color = Subly.colors.red, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(if (register) "Создать аккаунт" else "Войти", { focus.clearFocus(); onSubmit(email, password, register) }, enabled = valid, loading = busy)

        Spacer(Modifier.height(24.dp))
        Text(
            "Регистрируясь, вы соглашаетесь с политикой конфиденциальности. Мы храним только почту и список подписок — без данных карт и банков.",
            style = Subly.type.footnote, color = Subly.colors.secondary, textAlign = TextAlign.Center,
        )
        TextButton(onClick = onPrivacy) { Text("Политика конфиденциальности", style = Subly.type.footnote, color = Subly.colors.accent) }
        TextButton(onClick = { serverDialog = true }) {
            Text("Сервер: ${serverUrl.removePrefix("https://")}", style = Subly.type.footnote, color = Subly.colors.tertiary)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (serverDialog) ServerDialog(serverUrl, onDismiss = { serverDialog = false }, onSave = { serverDialog = false; onServer(it) })
}

@Composable
fun ServerDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var url by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Адрес сервера") },
        text = {
            Column {
                Text("Для своего сервера или локальной проверки. В эмуляторе компьютер доступен как http://10.0.2.2:8080",
                    style = Subly.type.footnote, color = Subly.colors.secondary)
                Spacer(Modifier.height(12.dp))
                Group { TextInput(url, { url = it.trim() }, "https://subly.antonshkel.ru", keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri)) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }, enabled = url.length > 4) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Знак приложения, нарисованный кодом (тот же, что на иконке). */
@Composable
fun AppMark(sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp * 0.24f).dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E8BFF), Color(0xFF0058D6)))),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size((sizeDp * 0.5f).dp)) {
            val sw = size.minDimension * 0.15f
            drawArc(Color.White, -90f, 300f, false, topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = sw * 0.62f,
                center = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.3f))
        }
    }
}
