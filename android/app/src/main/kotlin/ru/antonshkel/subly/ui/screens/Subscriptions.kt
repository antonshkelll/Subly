package ru.antonshkel.subly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.data.User
import ru.antonshkel.subly.domain.dayMonthShort
import ru.antonshkel.subly.domain.next
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.ui.components.AddButton
import ru.antonshkel.subly.ui.components.Avatar
import ru.antonshkel.subly.ui.components.Badge
import ru.antonshkel.subly.ui.components.Card
import ru.antonshkel.subly.ui.components.Divider
import ru.antonshkel.subly.ui.components.EmptyState
import ru.antonshkel.subly.ui.components.LargeTitle
import ru.antonshkel.subly.ui.components.Segmented
import ru.antonshkel.subly.ui.components.pressable
import ru.antonshkel.subly.ui.theme.Subly
import ru.antonshkel.subly.ui.theme.parseColor
import java.time.LocalDate

@Composable
fun SubscriptionsScreen(
    user: User?,
    subs: List<Subscription>,
    contentPadding: PaddingValues,
    onAdd: () -> Unit,
    onOpen: (Subscription) -> Unit,
    onUpgrade: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    var sort by rememberSaveable { mutableIntStateOf(0) }
    val sorted = remember(subs, sort, today) {
        when (sort) {
            0 -> subs.sortedBy { it.next(today) }
            1 -> subs.sortedByDescending { it.monthlyKopecks }
            else -> subs.sortedBy { it.name.lowercase() }
        }
    }
    val last = sorted.lastIndex
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") { LargeTitle("Подписки", subtitle = "${subs.size} · ${rub(subs.sumOf { it.monthlyKopecks })} в месяц") { AddButton(onAdd) } }
        if (subs.isEmpty()) {
            item(key = "empty") {
                Spacer(Modifier.height(24.dp))
                Card {
                    EmptyState("Пока пусто", "Добавьте сервисы, за которые платите: кино, музыку, облако, связь.", "Добавить подписку", onAdd) { EmptyArt() }
                }
            }
            return@LazyColumn
        }
        item(key = "sort") {
            Segmented(listOf("По дате", "По цене", "По имени"), sort, { sort = it }, Modifier.padding(top = 12.dp, bottom = 12.dp))
        }
        // Каждая строка — отдельный элемент списка: LazyColumn рисует только видимые,
        // а animateItem плавно переставляет их при смене сортировки.
        itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
            val shape = when {
                last == 0 -> RoundedCornerShape(18.dp)
                i == 0 -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                i == last -> RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Column(Modifier.animateItem().clip(shape).background(Subly.colors.card)) {
                if (i > 0) Divider(start = 70.dp)
                SubscriptionRow(s, today) { onOpen(s) }
            }
        }
        if (user != null && !user.isPro && user.subscriptionLimit != null) {
            item(key = "limit") {
                Card(Modifier.padding(top = 16.dp).pressable(onClick = onUpgrade)) {
                    Text("${subs.size} из ${user.subscriptionLimit} в бесплатной версии", style = Subly.type.headline, color = Subly.colors.label)
                    Text("В Pro — без ограничений, плюс советы, как сэкономить", style = Subly.type.subhead, color = Subly.colors.accent)
                }
            }
        }
    }
}

@Composable
fun SubscriptionRow(s: Subscription, today: LocalDate, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressable(scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(s.name, parseColor(s.color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = Subly.type.headline, color = Subly.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.isTrial) { Spacer(Modifier.width(6.dp)); Badge("пробный", Subly.colors.orange) }
            }
            Text("${s.period.adjective} · ${s.next(today).dayMonthShort()}", style = Subly.type.footnote, color = Subly.colors.secondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(rub(s.priceKopecks), style = Subly.type.headline, color = Subly.colors.label)
            if (s.period != ru.antonshkel.subly.data.Period.MONTH)
                Text("${rub(s.monthlyKopecks)}/мес", style = Subly.type.footnote, color = Subly.colors.secondary)
        }
    }
}
