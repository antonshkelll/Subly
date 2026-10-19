package ru.antonshkel.subly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.CATEGORIES
import ru.antonshkel.subly.data.PALETTE
import ru.antonshkel.subly.data.PRESETS
import ru.antonshkel.subly.data.Period
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.data.SubscriptionInput
import ru.antonshkel.subly.domain.kopecksToInput
import ru.antonshkel.subly.domain.longDate
import ru.antonshkel.subly.domain.monthly
import ru.antonshkel.subly.domain.parseRub
import ru.antonshkel.subly.domain.plural
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.ui.components.Avatar
import ru.antonshkel.subly.ui.components.Chip
import ru.antonshkel.subly.ui.components.Divider
import ru.antonshkel.subly.ui.components.FormRow
import ru.antonshkel.subly.ui.components.Group
import ru.antonshkel.subly.ui.components.Segmented
import ru.antonshkel.subly.ui.components.Stepper
import ru.antonshkel.subly.ui.components.TextInput
import ru.antonshkel.subly.ui.components.Toggle
import ru.antonshkel.subly.ui.components.pressable
import ru.antonshkel.subly.ui.theme.Subly
import ru.antonshkel.subly.ui.theme.parseColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Экран добавления и изменения подписки. Открывается снизу поверх вкладок, как модальный лист в iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    initial: Subscription?,
    busy: Boolean,
    onCancel: () -> Unit,
    onSave: (SubscriptionInput) -> Unit,
    onDelete: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var price by rememberSaveable { mutableStateOf(initial?.priceKopecks?.let(::kopecksToInput).orEmpty()) }
    var period by rememberSaveable { mutableIntStateOf(initial?.period?.ordinal ?: Period.MONTH.ordinal) }
    var date by rememberSaveable { mutableStateOf(initial?.nextCharge ?: today.plusDays(1).toString()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "video") }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: PALETTE.first()) }
    var remind by rememberSaveable { mutableIntStateOf(initial?.remindDaysBefore ?: 1) }
    var trial by rememberSaveable { mutableStateOf(initial?.isTrial ?: false) }
    var notes by rememberSaveable { mutableStateOf(initial?.notes.orEmpty()) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val kopecks = parseRub(price)
    val valid = name.isNotBlank() && kopecks != null
    val p = Period.entries[period]

    Column(Modifier.fillMaxSize().background(Subly.colors.background).statusBarsPadding().imePadding()) {
        // Шапка как у модального окна iOS: «Отмена» — заголовок — «Готово»
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Отмена", style = Subly.type.body, color = Subly.colors.accent) }
            Text(if (initial == null) "Новая подписка" else "Подписка", style = Subly.type.headline, color = Subly.colors.label,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            TextButton(enabled = valid && !busy, onClick = {
                onSave(SubscriptionInput(name.trim(), kopecks ?: 0, p, date, category, color, remind, trial, notes.trim()))
            }) {
                Text("Готово", style = Subly.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (valid && !busy) Subly.colors.accent else Subly.colors.tertiary)
            }
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding()) {
            // Превью карточки — сразу видно, как подписка будет выглядеть в списке
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(name.ifBlank { "?" }, parseColor(color), size = 72.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (kopecks != null) "${rub(kopecks)} · ${p.title.lowercase()}" else "Укажите цену",
                    style = Subly.type.subhead, color = Subly.colors.secondary,
                )
            }

            if (initial == null) {
                Text("Популярное", style = Subly.type.footnote, color = Subly.colors.secondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                    items(PRESETS, key = { it.name }) { preset ->
                        Chip(preset.name, selected = name == preset.name, dot = parseColor(preset.color), onClick = {
                            name = preset.name; category = preset.category; color = preset.color
                        })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Group {
                TextInput(name, { if (it.length <= 60) name = it }, "Название, например «Кинопоиск»",
                    keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextInput(price, { v -> if (v.length <= 10 && v.all { it.isDigit() || it == ',' || it == '.' }) price = v }, "Цена",
                        modifier = Modifier.weight(1f),
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done))
                    Text("₽", style = Subly.type.body, color = Subly.colors.secondary, modifier = Modifier.padding(end = 16.dp))
                }
            }
            if (kopecks != null && p != Period.MONTH) {
                Text("≈ ${rub(monthly(kopecks, p))} в месяц", style = Subly.type.footnote, color = Subly.colors.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp))
            }

            Label("Как часто списывается")
            Segmented(Period.entries.map { it.title }, period, { period = it })

            Label("Списание")
            Group {
                FormRow(if (trial) "Конец пробного периода" else "Следующее списание", onClick = { showDate = true }) {
                    Text(LocalDate.parse(date).longDate(), style = Subly.type.body, color = Subly.colors.accent)
                }
                Divider()
                FormRow("Пробный период") { Toggle(trial) { trial = it } }
                Divider()
                FormRow("Напомнить") {
                    Stepper(
                        text = if (remind == 0) "в тот же день" else "за $remind ${plural(remind.toLong(), "день", "дня", "дней")}",
                        canMinus = remind > 0, canPlus = remind < 7, onMinus = { remind-- }, onPlus = { remind++ },
                    )
                }
            }

            Label("Категория")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CATEGORIES, key = { it.key }) { c -> Chip(c.title, category == c.key, { category = c.key }, dot = CategoryColors[c.key]) }
            }

            Label("Цвет")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PALETTE.forEach { hex ->
                    val selected = hex.equals(color, ignoreCase = true)
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(parseColor(hex))
                            .then(if (selected) Modifier.border(3.dp, Subly.colors.card, CircleShape) else Modifier)
                            .pressable(scale = 0.85f) { color = hex },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White)) }
                }
            }

            Label("Заметка")
            Group {
                TextInput(notes, { if (it.length <= 500) notes = it }, "Например: тариф семейный, отменить в декабре", singleLine = false)
            }

            if (initial != null) {
                Spacer(Modifier.height(28.dp))
                Group {
                    FormRow("Удалить подписку", onClick = { confirmDelete = true }, labelColor = Subly.colors.red) {}
                }
                Text("Удаление только убирает подписку из Subly. Отменить её нужно в самом сервисе.",
                    style = Subly.type.footnote, color = Subly.colors.secondary, modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp))
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    showDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) { DatePicker(state, showModeToggle = false) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «$name»?") },
            text = { Text("Подписка пропадёт из списка и статистики.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить", color = Subly.colors.red) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = Subly.type.footnote, color = Subly.colors.secondary, modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp))
}

