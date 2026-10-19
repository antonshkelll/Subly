package ru.antonshkel.subly.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.antonshkel.subly.ui.components.PrimaryButton
import ru.antonshkel.subly.ui.theme.Subly
import kotlin.math.absoluteValue

private data class Page(val title: String, val text: String, val art: @Composable () -> Unit)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Page("Все подписки\nв одном месте", "Кино, музыка, облако, связь. Сразу видно, сколько уходит в месяц и в год.") { StackArt() },
        Page("Напомним\nдо списания", "За день-два до оплаты придёт уведомление. Особенно полезно, когда заканчивается пробный период.") { BellArt() },
        Page("Найдём, где\nвы переплачиваете", "Два музыкальных сервиса, забытый пробный период, дорогой тариф — Subly подскажет.") { RingArt() },
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Subly.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Пропустить", style = Subly.type.body, color = Subly.colors.secondary) }
        }
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            // Параллакс: картинка и текст сдвигаются с разной скоростью при листании.
            val offset = (pager.currentPage - i + pager.currentPageOffsetFraction)
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.height(260.dp).graphicsLayer { translationX = offset * 220f; alpha = 1f - offset.absoluteValue * 0.6f }, contentAlignment = Alignment.Center) {
                    pages[i].art()
                }
                Spacer(Modifier.height(32.dp))
                Text(pages[i].title, style = Subly.type.title1, color = Subly.colors.label, textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer { translationX = offset * 80f })
                Spacer(Modifier.height(12.dp))
                Text(pages[i].text, style = Subly.type.body, color = Subly.colors.secondary, textAlign = TextAlign.Center)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val w by animateDpAsState(if (pager.currentPage == i) 22.dp else 8.dp, label = "dot")
                Box(Modifier.padding(horizontal = 4.dp).size(width = w, height = 8.dp).clip(CircleShape)
                    .background(if (pager.currentPage == i) Subly.colors.accent else Subly.colors.fill))
            }
        }
        PrimaryButton(
            if (pager.currentPage == pages.lastIndex) "Начать" else "Дальше",
            onClick = { if (pager.currentPage == pages.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/** Лёгкое «парение» — одна бесконечная анимация только на онбординге, через graphicsLayer. */
@Composable
private fun floatY(periodMs: Int, amplitude: Float): Float {
    val t = rememberInfiniteTransition(label = "float")
    val v by t.animateFloat(-amplitude, amplitude, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    return v
}

@Composable
private fun StackArt() {
    val y = floatY(2200, 8f)
    Box(contentAlignment = Alignment.Center) {
        listOf(Triple(Color(0xFFFF9500), -50, -10f), Triple(Color(0xFF34C759), 50, 10f), Triple(Color(0xFF007AFF), 0, 0f)).forEachIndexed { i, (c, x, r) ->
            Box(
                Modifier.offset(x = x.dp).graphicsLayer { rotationZ = r; translationY = if (i == 2) y * 2 else -y }
                    .size(width = 150.dp, height = 96.dp).clip(RoundedCornerShape(22.dp)).background(c).padding(16.dp),
            ) {
                Column {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.9f)))
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.size(width = 80.dp, height = 8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                }
            }
        }
    }
}

@Composable
private fun BellArt() {
    val y = floatY(1800, 6f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("Кинопоиск: списание завтра" to Color(0xFFFF6A00), "Пробный период заканчивается" to Color(0xFFFF9500)).forEachIndexed { i, (t, c) ->
            Row(
                Modifier.graphicsLayer { translationY = if (i == 0) y else -y }.width(270.dp).clip(RoundedCornerShape(18.dp))
                    .background(Subly.colors.card).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Subly · сейчас", style = Subly.type.caption, color = Subly.colors.secondary)
                    Text(t, style = Subly.type.subhead, color = Subly.colors.label)
                }
            }
        }
    }
}

@Composable
private fun RingArt() {
    val y = floatY(2600, 6f)
    Box(Modifier.graphicsLayer { translationY = y }, contentAlignment = Alignment.Center) {
        ru.antonshkel.subly.ui.components.Donut(
            listOf(
                ru.antonshkel.subly.ui.components.Slice(Color(0xFFFF3B30), 4f),
                ru.antonshkel.subly.ui.components.Slice(Color(0xFFAF52DE), 3f),
                ru.antonshkel.subly.ui.components.Slice(Color(0xFF007AFF), 2f),
                ru.antonshkel.subly.ui.components.Slice(Color(0xFF34C759), 1f),
            ),
            size = 190.dp, stroke = 26.dp,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("−2 388 ₽", style = Subly.type.title2, color = Subly.colors.green)
            Text("в год", style = Subly.type.footnote, color = Subly.colors.secondary)
        }
    }
}
