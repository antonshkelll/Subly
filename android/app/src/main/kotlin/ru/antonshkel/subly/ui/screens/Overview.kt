package ru.antonshkel.subly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.data.User
import ru.antonshkel.subly.data.categoryTitle
import ru.antonshkel.subly.domain.dayMonthShort
import ru.antonshkel.subly.domain.daysLeft
import ru.antonshkel.subly.domain.fullDate
import ru.antonshkel.subly.domain.next
import ru.antonshkel.subly.domain.nextCharge
import ru.antonshkel.subly.domain.plural
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.domain.rubRound
import ru.antonshkel.subly.domain.whenText
import ru.antonshkel.subly.ui.components.AddButton
import ru.antonshkel.subly.ui.components.AnimatedRub
import ru.antonshkel.subly.ui.components.Avatar
import ru.antonshkel.subly.ui.components.Badge
import ru.antonshkel.subly.ui.components.Card
import ru.antonshkel.subly.ui.components.CardShape
import ru.antonshkel.subly.ui.components.Divider
import ru.antonshkel.subly.ui.components.Donut
import ru.antonshkel.subly.ui.components.EmptyState
import ru.antonshkel.subly.ui.components.Group
import ru.antonshkel.subly.ui.components.LargeTitle
import ru.antonshkel.subly.ui.components.SectionTitle
import ru.antonshkel.subly.ui.components.Slice
import ru.antonshkel.subly.ui.components.pressable
import ru.antonshkel.subly.ui.theme.Subly
import ru.antonshkel.subly.ui.theme.parseColor
import java.time.LocalDate

@Immutable
data class Summary(
    val monthly: Long,
    val yearly: Long,
    val count: Int,
    val categories: List<Pair<String, Long>>,      // категория → в месяц, по убыванию
    val upcoming: List<Subscription>,
    val next30: Long,
)

fun summarize(subs: List<Subscription>, today: LocalDate = LocalDate.now()): Summary {
    val monthly = subs.sumOf { it.monthlyKopecks }
    val upcoming = subs.filter { it.daysLeft(today) <= 30 }.sortedBy { it.next(today) }
    return Summary(
        monthly = monthly,
        yearly = monthly * 12,
        count = subs.size,
        categories = subs.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.monthlyKopecks } }
            .entries.sortedByDescending { it.value }.map { it.key to it.value },
        upcoming = upcoming,
        next30 = subs.sumOf { it.priceKopecks * it.chargesWithin(today, 30) },
    )
}

/** Сколько раз подписка спишется за ближайшие [days] дней: еженедельная за 30 дней списывается 4–5 раз. */
private fun Subscription.chargesWithin(today: LocalDate, days: Long): Long {
    val anchor = LocalDate.parse(anchorDate)
    val end = today.plusDays(days)
    var n = 0L
    var d = next(today)
    while (!d.isAfter(end)) {
        n++
        d = nextCharge(anchor, period, d.plusDays(1))
    }
    return n
}

/** Цвет категории на диаграмме — системные цвета iOS по порядку. */
val CategoryColors = mapOf(
    "video" to Color(0xFFFF3B30), "music" to Color(0xFFAF52DE), "cloud" to Color(0xFF007AFF),
    "software" to Color(0xFF34C759), "games" to Color(0xFFFF9500), "education" to Color(0xFF5856D6),
    "news" to Color(0xFFFFCC00), "fitness" to Color(0xFF30B0C7), "telecom" to Color(0xFF8E8E93), "other" to Color(0xFFA2845E),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    user: User?,
    subs: List<Subscription>,
    refreshing: Boolean,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Subscription) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val summary = remember(subs, today) { summarize(subs, today) }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "title") {
                LargeTitle("Обзор", subtitle = today.fullDate()) { AddButton(onAdd) }
            }
            if (subs.isEmpty()) {
                item(key = "empty") {
                    Spacer(Modifier.height(24.dp))
                    Card {
                        EmptyState(
                            title = "Добавьте первую подписку",
                            text = "Subly посчитает, сколько уходит в месяц и в год, и напомнит до списания.",
                            action = "Добавить", onAction = onAdd,
                        ) { EmptyArt() }
                    }
                }
                return@LazyColumn
            }
            item(key = "hero") { Spacer(Modifier.height(16.dp)); HeroCard(summary) }
            item(key = "chart") { SectionTitle("По категориям"); CategoriesCard(summary) }
            item(key = "upcoming-title") {
                SectionTitle("Ближайшие 30 дней")
            }
            if (summary.upcoming.isEmpty()) {
                item(key = "no-upcoming") {
                    Card { Text("В ближайший месяц списаний нет", style = Subly.type.subhead, color = Subly.colors.secondary) }
                }
            } else {
                item(key = "upcoming") {
                    Group {
                        summary.upcoming.take(6).forEachIndexed { i, s ->
                            if (i > 0) Divider(start = 70.dp)
                            UpcomingRow(s, today, onClick = { onOpen(s) })
                        }
                    }
                }
            }
            if (user != null && !user.isPro && user.subscriptionLimit != null) {
                item(key = "limit") {
                    Text(
                        "Бесплатно: ${subs.size} из ${user.subscriptionLimit} подписок",
                        style = Subly.type.footnote, color = Subly.colors.secondary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(s: Summary) {
    val c = Subly.colors
    // Мягкий градиент вместо размытия: выглядит «дорого», а стоит как обычная заливка.
    val brush = remember(c.isDark) {
        if (c.isDark) Brush.linearGradient(listOf(Color(0xFF0A2A5C), Color(0xFF1C1C1E)))
        else Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))
    }
    Column(Modifier.fillMaxWidth().clip(CardShape).background(brush).padding(20.dp)) {
        Text("В месяц", style = Subly.type.subhead, color = Color.White.copy(alpha = 0.8f))
        AnimatedRub(s.monthly, Subly.type.hero, Color.White)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            HeroStat("В год", rubRound(s.yearly))
            HeroStat("Подписок", s.count.toString())
            HeroStat("За 30 дней", rub(s.next30))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    Column {
        Text(label, style = Subly.type.caption, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = Subly.type.headline, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun CategoriesCard(s: Summary) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Donut(s.categories.map { Slice(CategoryColors[it.first] ?: Color.Gray, it.second.toFloat()) }, size = 128.dp, stroke = 16.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.categories.size.toString(), style = Subly.type.title2, color = Subly.colors.label)
                    Text(plural(s.categories.size.toLong(), "категория", "категории", "категорий"), style = Subly.type.caption, color = Subly.colors.secondary)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = s.monthly.coerceAtLeast(1)
                s.categories.take(5).forEach { (key, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(CategoryColors[key] ?: Color.Gray))
                        Spacer(Modifier.width(8.dp))
                        Text(categoryTitle(key), style = Subly.type.subhead, color = Subly.colors.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${value * 100 / total}%", style = Subly.type.subhead, color = Subly.colors.secondary)
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingRow(s: Subscription, today: LocalDate, onClick: () -> Unit) {
    val left = s.daysLeft(today)
    Row(Modifier.fillMaxWidth().pressable(scale = 0.985f, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(s.name, parseColor(s.color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = Subly.type.headline, color = Subly.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.isTrial) { Spacer(Modifier.width(6.dp)); Badge("пробный", Subly.colors.orange) }
            }
            Text("${whenText(left).replaceFirstChar { it.uppercase() }} · ${s.next(today).dayMonthShort()}", style = Subly.type.footnote,
                color = if (left <= 1) Subly.colors.orange else Subly.colors.secondary)
        }
        Text(rub(s.priceKopecks), style = Subly.type.headline.copy(fontWeight = FontWeight.SemiBold), color = Subly.colors.label)
    }
}

@Composable
fun EmptyArt() {
    // Три «карточки подписок» веером — нарисованы кодом, без картинок в APK.
    Box(Modifier.size(width = 160.dp, height = 96.dp), contentAlignment = Alignment.Center) {
        listOf(Triple(-12f, Color(0xFFFF9500), (-38).dp), Triple(12f, Color(0xFF34C759), 38.dp), Triple(0f, Color(0xFF007AFF), 0.dp))
            .forEach { (rot, color, x) ->
                Box(
                    Modifier.offset(x = x).graphicsLayer { rotationZ = rot }.size(68.dp)
                        .clip(RoundedCornerShape(19.dp)).background(color),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(width = 30.dp, height = 6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))) }
            }
    }
}
