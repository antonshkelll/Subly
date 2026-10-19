package ru.antonshkel.subly.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.Insight
import ru.antonshkel.subly.data.User
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.ui.components.Card
import ru.antonshkel.subly.ui.components.LargeTitle
import ru.antonshkel.subly.ui.components.PrimaryButton
import ru.antonshkel.subly.ui.components.SecondaryButton
import ru.antonshkel.subly.ui.theme.Subly

@Composable
fun InsightsScreen(
    user: User?,
    insights: List<Insight>?,
    error: String?,
    subsCount: Int,
    contentPadding: PaddingValues,
    onUpgrade: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "title") { LargeTitle("Советы", subtitle = "Где можно сэкономить") }
        when {
            user?.isPro != true -> item(key = "locked") { Spacer(Modifier.height(16.dp)); LockedInsights(onUpgrade) }
            subsCount == 0 -> item(key = "empty") {
                Spacer(Modifier.height(16.dp))
                Card { Text("Добавьте хотя бы одну подписку — и здесь появятся советы.", style = Subly.type.subhead, color = Subly.colors.secondary) }
            }
            insights == null && error != null -> item(key = "error") {
                Spacer(Modifier.height(16.dp))
                Card {
                    Text(error, style = Subly.type.subhead, color = Subly.colors.secondary)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Повторить", onRetry)
                }
            }
            insights == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Subly.colors.accent) }
            }
            else -> {
                val saving = potentialSaving(insights)
                if (saving > 0) item(key = "saving") {
                    Spacer(Modifier.height(16.dp))
                    Card {
                        Text("Можно сэкономить", style = Subly.type.subhead, color = Subly.colors.secondary)
                        Text("до ${rub(saving)} в год", style = Subly.type.title1, color = Subly.colors.green)
                    }
                }
                itemsIndexed(insights, key = { i, it -> "${it.kind}-$i" }) { i, insight ->
                    // Карточки появляются по очереди — только при первом показе, без бесконечных анимаций.
                    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(visible, enter = fadeIn(tween(300, delayMillis = 60 * i)) + slideInVertically(tween(300, delayMillis = 60 * i)) { it / 4 }) {
                        InsightCard(insight, Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Сколько можно сэкономить. Один и тот же сервис может попасть в два совета
 * (пробный период и дубль) — такие советы не складываем, берём больший.
 */
fun potentialSaving(insights: List<Insight>): Long {
    val taken = mutableSetOf<Long>()
    var total = 0L
    insights.filter { (it.kind == "duplicate" || it.kind == "trial") && (it.savingKopecks ?: 0) > 0 }
        .sortedByDescending { it.savingKopecks }
        .forEach { if (it.subscriptionIds.none(taken::contains)) { total += it.savingKopecks!!; taken += it.subscriptionIds } }
    return total
}

private data class Look(val icon: ImageVector, val color: @Composable () -> Color)

private val looks = mapOf(
    "total" to Look(Icons.Rounded.Info) { Subly.colors.accent },
    "trial" to Look(Icons.Rounded.Warning) { Subly.colors.orange },
    "duplicate" to Look(Icons.Rounded.Star) { Subly.colors.purple },
    "weekly" to Look(Icons.Rounded.Refresh) { Subly.colors.red },
    "share" to Look(Icons.Rounded.Info) { Subly.colors.red },
    "upcoming" to Look(Icons.Rounded.DateRange) { Subly.colors.green },
    "entertainment" to Look(Icons.Rounded.ThumbUp) { Subly.colors.accent },
)

@Composable
fun InsightCard(insight: Insight, modifier: Modifier = Modifier) {
    val look = looks[insight.kind] ?: looks.getValue("total")
    val color = look.color()
    Card(modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(look.icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(insight.title, style = Subly.type.headline, color = Subly.colors.label)
                Spacer(Modifier.height(4.dp))
                Text(insight.text, style = Subly.type.subhead, color = Subly.colors.secondary)
                insight.savingKopecks?.takeIf { insight.kind == "duplicate" || insight.kind == "trial" }?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("Экономия до ${rub(it)} в год", style = Subly.type.footnote, color = Subly.colors.green)
                }
            }
        }
    }
}

@Composable
private fun LockedInsights(onUpgrade: () -> Unit) {
    Card {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Subly.colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = Subly.colors.accent)
        }
        Spacer(Modifier.height(14.dp))
        Text("Узнайте, где вы переплачиваете", style = Subly.type.title2, color = Subly.colors.label)
        Spacer(Modifier.height(6.dp))
        Text("Subly найдёт сервисы-дубли, пробные периоды, которые вот-вот станут платными, и подписки, которые съедают большую часть бюджета.",
            style = Subly.type.subhead, color = Subly.colors.secondary)
        Spacer(Modifier.height(16.dp))
        // «Заглушки» советов вместо размытия: blur дорогой и работает только на Android 12+.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0.9f, 0.7f, 0.8f).forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Subly.colors.fill))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.fillMaxWidth(w).height(12.dp).clip(CircleShape).background(Subly.colors.fill))
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Открыть с Subly Pro", onUpgrade)
    }
}
