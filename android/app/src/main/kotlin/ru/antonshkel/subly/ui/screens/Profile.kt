package ru.antonshkel.subly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.BuildConfig
import ru.antonshkel.subly.data.Prefs
import ru.antonshkel.subly.data.ThemeMode
import ru.antonshkel.subly.data.User
import ru.antonshkel.subly.domain.longDate
import ru.antonshkel.subly.ui.components.Badge
import ru.antonshkel.subly.ui.components.CardShape
import ru.antonshkel.subly.ui.components.Divider
import ru.antonshkel.subly.ui.components.FormRow
import ru.antonshkel.subly.ui.components.Group
import ru.antonshkel.subly.ui.components.LargeTitle
import ru.antonshkel.subly.ui.components.Segmented
import ru.antonshkel.subly.ui.components.Toggle
import ru.antonshkel.subly.ui.components.pressable
import ru.antonshkel.subly.ui.theme.Subly
import java.time.Instant
import java.time.ZoneId

@Composable
fun ProfileScreen(
    user: User?,
    prefs: Prefs,
    contentPadding: PaddingValues,
    onUpgrade: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onReminders: (Boolean) -> Unit,
    onExport: () -> Unit,
    onRestore: () -> Unit,
    onServer: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    val isPro = user?.isPro == true

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") { LargeTitle("Профиль") }
        item(key = "account") {
            Spacer(Modifier.height(16.dp))
            Group {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF64D2FF), Color(0xFF0A84FF)))),
                        contentAlignment = Alignment.Center,
                    ) { Text(user?.email?.take(1)?.uppercase() ?: "?", style = Subly.type.title2, color = Color.White) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user?.email ?: "—", style = Subly.type.headline, color = Subly.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        if (isPro) {
                            val until = user?.proUntil?.let { runCatching { Instant.parse(it).atZone(ZoneId.systemDefault()).toLocalDate().longDate() }.getOrNull() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge("PRO", Subly.colors.accent)
                                if (until != null) Text("  до $until", style = Subly.type.footnote, color = Subly.colors.secondary)
                            }
                        } else Badge("FREE", Subly.colors.secondary)
                    }
                }
            }
        }
        if (!isPro) item(key = "upsell") {
            Spacer(Modifier.height(16.dp))
            Column(
                Modifier.fillMaxWidth().clip(CardShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFFBF5AF2))))
                    .pressable(onClick = onUpgrade).padding(20.dp),
            ) {
                Text("Subly Pro", style = Subly.type.title2, color = Color.White)
                Text("Безлимит, советы по экономии и экспорт. От 99 ₽ в месяц.", style = Subly.type.subhead, color = Color.White.copy(alpha = 0.9f))
            }
        }
        item(key = "settings") {
            Header("Настройки")
            Group {
                FormRow("Напоминания о списаниях") { Toggle(prefs.reminders, onReminders) }
                Divider()
                Column(Modifier.padding(16.dp)) {
                    Text("Оформление", style = Subly.type.body, color = Subly.colors.label)
                    Spacer(Modifier.height(10.dp))
                    Segmented(ThemeMode.entries.map { it.title }, prefs.theme.ordinal, { onTheme(ThemeMode.entries[it]) })
                }
            }
        }
        item(key = "data") {
            Header("Данные")
            Group {
                FormRow("Экспорт в CSV", onClick = if (isPro) onExport else onUpgrade) {
                    if (!isPro) Badge("PRO", Subly.colors.accent) else Chevron()
                }
                Divider()
                FormRow("Проверить оплату", onClick = onRestore) { Chevron() }
                Divider()
                FormRow("Сервер", onClick = onServer) {
                    Text(prefs.serverUrl.removePrefix("https://").removePrefix("http://"), style = Subly.type.subhead,
                        color = Subly.colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp))
                }
            }
        }
        item(key = "about") {
            Header("О приложении")
            Group {
                FormRow("Политика конфиденциальности", onClick = { onOpenUrl(prefs.serverUrl + "/privacy") }) { Chevron() }
                Divider()
                FormRow("Исходный код на GitHub", onClick = { onOpenUrl("https://github.com/antonshkelll/subly") }) { Chevron() }
                Divider()
                FormRow("Версия") { Text(BuildConfig.VERSION_NAME, style = Subly.type.body, color = Subly.colors.secondary) }
            }
        }
        item(key = "danger") {
            Spacer(Modifier.height(28.dp))
            Group {
                FormRow("Выйти", onClick = { confirmLogout = true }, labelColor = Subly.colors.accent) {}
                Divider()
                FormRow("Удалить аккаунт", onClick = { confirmDelete = true }, labelColor = Subly.colors.red) {}
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("Выйти из аккаунта?") },
        text = { Text("Подписки останутся на сервере, войдите снова в любой момент.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Выйти") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Удалить аккаунт?") },
        text = { Text("Аккаунт, все подписки и история будут удалены с сервера без возможности восстановления.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteAccount() }) { Text("Удалить", color = Subly.colors.red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
    )
}

@Composable
private fun Header(text: String) {
    Text(text.uppercase(), style = Subly.type.footnote, color = Subly.colors.secondary, modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 8.dp))
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Subly.colors.tertiary)
}
