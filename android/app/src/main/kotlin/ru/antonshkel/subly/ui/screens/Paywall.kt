package ru.antonshkel.subly.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.Plan
import ru.antonshkel.subly.data.PlansResponse
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.domain.rubRound
import ru.antonshkel.subly.ui.components.Badge
import ru.antonshkel.subly.ui.components.CloseButton
import ru.antonshkel.subly.ui.components.PrimaryButton
import ru.antonshkel.subly.ui.components.pressable
import ru.antonshkel.subly.ui.theme.Subly

val DEFAULT_PLANS = listOf(
    Plan("pro_month", "Subly Pro на 1 месяц", 149_00, 1),
    Plan("pro_year", "Subly Pro на 12 месяцев", 1190_00, 12),
)

@Composable
fun PaywallScreen(
    plans: PlansResponse?,
    busy: Boolean,
    isPro: Boolean,
    onClose: () -> Unit,
    onBuy: (String) -> Unit,
) {
    val list = plans?.plans ?: DEFAULT_PLANS
    var selected by rememberSaveable { mutableStateOf("pro_year") }
    val plan = list.firstOrNull { it.code == selected } ?: list.last()

    Column(Modifier.fillMaxSize().background(Subly.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) { CloseButton(onClose) }
        AnimatedContent(isPro, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() }, label = "pro") { pro ->
            if (pro) ProWelcome(onClose) else Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    Box(
                        Modifier.size(72.dp).clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFFBF5AF2)))),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
                    Spacer(Modifier.height(18.dp))
                    Text("Subly Pro", style = Subly.type.largeTitle, color = Subly.colors.label)
                    Text("Всё, чтобы платить только за то, чем пользуетесь.", style = Subly.type.body, color = Subly.colors.secondary)
                    Spacer(Modifier.height(24.dp))
                    Feature(Icons.Rounded.List, "Без ограничений", "Сколько угодно подписок вместо пяти")
                    Feature(Icons.Rounded.Star, "Советы по экономии", "Дубли, пробные периоды, дорогие тарифы")
                    Feature(Icons.Rounded.Share, "Экспорт в Excel", "Все подписки одним файлом CSV")
                    Feature(Icons.Rounded.Favorite, "Поддержка проекта", "Subly без рекламы и продажи данных")
                    Spacer(Modifier.height(20.dp))
                    list.forEach { p ->
                        PlanCard(p, p.code == plan.code, monthlyPrice = list.firstOrNull { it.months == 1L }?.priceKopecks) { selected = p.code }
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    PrimaryButton(
                        text = if (plans?.paymentsAvailable == false) "Оплата скоро появится" else "Оплатить ${rub(plan.priceKopecks)}",
                        onClick = { onBuy(plan.code) }, loading = busy, enabled = plans?.paymentsAvailable != false,
                    )
                    Text(
                        if (plans?.demo == true) "Демо-режим сервера: Pro включится без оплаты."
                        else "Оплата картой или через СБП в ЮKassa. Без автопродления — продлите, когда захотите.",
                        style = Subly.type.footnote, color = Subly.colors.secondary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Subly.colors.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Subly.colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = Subly.type.headline, color = Subly.colors.label)
            Text(text, style = Subly.type.subhead, color = Subly.colors.secondary)
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, monthlyPrice: Long?, onClick: () -> Unit) {
    val border by animateFloatAsState(if (selected) 1f else 0f, spring(stiffness = Spring.StiffnessMedium), label = "plan")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Subly.colors.card)
            .border((2 * border).dp, Subly.colors.accent.copy(alpha = border), RoundedCornerShape(16.dp))
            .pressable(scale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (selected) Subly.colors.accent else Subly.colors.fill),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (plan.months == 1L) "Месяц" else "Год", style = Subly.type.headline, color = Subly.colors.label)
                if (plan.months > 1 && monthlyPrice != null) {
                    val discount = Math.round(100 - plan.priceKopecks * 100.0 / (monthlyPrice * plan.months))
                    if (discount > 0) { Spacer(Modifier.width(8.dp)); Badge("−$discount%", Subly.colors.green) }
                }
            }
            if (plan.months > 1) Text("${rubRound(plan.priceKopecks / plan.months)} в месяц", style = Subly.type.footnote, color = Subly.colors.secondary)
        }
        Text(rub(plan.priceKopecks), style = Subly.type.headline, color = Subly.colors.label)
    }
}

@Composable
private fun ProWelcome(onClose: () -> Unit) {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Subly.colors.green, modifier = Modifier.size(96.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value })
        Spacer(Modifier.height(20.dp))
        Text("Subly Pro активен", style = Subly.type.title1, color = Subly.colors.label)
        Spacer(Modifier.height(8.dp))
        Text("Спасибо! Советы и безлимит уже доступны.", style = Subly.type.body, color = Subly.colors.secondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        PrimaryButton("Отлично", onClose)
    }
}
