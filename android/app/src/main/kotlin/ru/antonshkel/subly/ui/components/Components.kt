package ru.antonshkel.subly.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.ui.theme.Subly
import kotlin.math.roundToLong

val CardShape = RoundedCornerShape(18.dp)

/**
 * Нажатие «как на iPhone»: элемент слегка сжимается на пружине, без ripple.
 * Анимируется только graphicsLayer — это не вызывает перерасчёт раскладки и дёшево даже на слабых телефонах.
 */
fun Modifier.pressable(enabled: Boolean = true, scale: Float = 0.97f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Subly.colors.card).padding(padding), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = Subly.type.title3, color = Subly.colors.label, modifier = Modifier.weight(1f))
        if (action != null) Text(action, style = Subly.type.subhead, color = Subly.colors.accent, modifier = Modifier.pressable(onClick = onAction))
    }
}

@Composable
fun LargeTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = Subly.type.caption, color = Subly.colors.secondary)
            Text(title, style = Subly.type.largeTitle, color = Subly.colors.label)
        }
        trailing()
    }
}

@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Subly.colors.accent, background: Color = Subly.colors.fill) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(background).pressable(scale = 0.9f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun AddButton(onClick: () -> Unit) = CircleButton(Icons.Rounded.Add, "Добавить подписку", onClick)

@Composable
fun CloseButton(onClick: () -> Unit) = CircleButton(Icons.Rounded.Close, "Закрыть", onClick, tint = Subly.colors.secondary)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Subly.colors.accent,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "btn")
    Box(
        modifier.fillMaxWidth().height(52.dp).graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(14.dp)).background(color)
            .pressable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, style = Subly.type.headline, color = Color.White)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Subly.colors.accent) {
    Box(
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(Subly.colors.card).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Subly.type.headline, color = color) }
}

/** Сумма, которая «досчитывается» до нового значения. Перерисовывается только этот Text. */
@Composable
fun AnimatedRub(kopecks: Long, style: TextStyle, color: Color = Subly.colors.label, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(kopecks) { anim.animateTo(kopecks.toFloat(), tween(700, easing = FastOutSlowInEasing)) }
    val shown = (if (anim.isRunning) anim.value / 100 else kopecks / 100f).roundToLong() * 100
    Text(rub(shown), style = style, color = color, modifier = modifier, maxLines = 1)
}

data class Slice(val color: Color, val value: Float)

/** Кольцевая диаграмма. Сектора «прорисовываются» по кругу при первом показе. */
@Composable
fun Donut(slices: List<Slice>, size: Dp, stroke: Dp, modifier: Modifier = Modifier, track: Color = Subly.colors.fill) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val total = slices.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Canvas(modifier.size(size)) {
        val sw = stroke.toPx()
        val arcSize = Size(this.size.width - sw, this.size.height - sw)
        val topLeft = Offset(sw / 2, sw / 2)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
        val gap = if (slices.size > 1) 3f else 0f
        var start = -90f
        slices.forEach { s ->
            val sweep = 360f * s.value / total * progress.value
            if (sweep > gap) drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

/** Сегментированный переключатель как в iOS: плашка выбора плавно переезжает. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(Subly.colors.fill).padding(2.dp)) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Subly.colors.cardElevated))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, style = Subly.type.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = Subly.colors.label, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun Avatar(name: String, color: Color, size: Dp = 42.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(color), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = Subly.type.headline.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
fun Divider(start: Dp = 16.dp) {
    Box(Modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(Subly.colors.separator))
}

/** Строка в сгруппированной карточке (как в «Настройках» iPhone). */
@Composable
fun FormRow(label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, labelColor: Color = Subly.colors.label, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Subly.type.body, color = labelColor, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(CardShape).background(Subly.colors.card), content = content)
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    actions: KeyboardActions = KeyboardActions.Default,
    visual: VisualTransformation = VisualTransformation.None,
    style: TextStyle = Subly.type.body,
    align: TextAlign = TextAlign.Start,
    singleLine: Boolean = true,
) {
    Box(modifier.heightIn(min = 50.dp).padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = style.copy(textAlign = align), color = Subly.colors.tertiary, modifier = Modifier.fillMaxWidth())
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = style.copy(color = Subly.colors.label, textAlign = align),
            cursorBrush = SolidColor(Subly.colors.accent),
            keyboardOptions = keyboard, keyboardActions = actions, visualTransformation = visual,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Subly.colors.green, checkedThumbColor = Color.White, checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Subly.colors.fill, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun Stepper(text: String, canMinus: Boolean, canPlus: Boolean, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Subly.type.body, color = Subly.colors.secondary, modifier = Modifier.padding(end = 10.dp))
        Row(Modifier.clip(RoundedCornerShape(9.dp)).background(Subly.colors.fill)) {
            StepperHalf("−", canMinus, onMinus)
            Box(Modifier.width(0.5.dp).height(32.dp).background(Subly.colors.separator))
            StepperHalf("+", canPlus, onPlus)
        }
    }
}

@Composable
private fun StepperHalf(sign: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(width = 46.dp, height = 32.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(sign, style = Subly.type.title3, color = if (enabled) Subly.colors.label else Subly.colors.tertiary)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, dot: Color? = null) {
    val bg = if (selected) Subly.colors.accent else Subly.colors.card
    val fg = if (selected) Color.White else Subly.colors.label
    Row(
        Modifier.clip(CircleShape).background(bg).pressable(scale = 0.94f, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else dot))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Subly.type.subhead, color = fg, maxLines = 1)
    }
}

/** Всплывающее сообщение сверху (вместо стандартного Snackbar — ближе к баннерам iOS). */
@Composable
fun Banner(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null, modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp))
                .background(if (Subly.colors.isDark) Subly.colors.cardElevated else Color(0xF21D1D1F))
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(message.orEmpty(), style = Subly.type.subhead, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}, art: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        art()
        Spacer(Modifier.height(6.dp))
        Text(title, style = Subly.type.title3, color = Subly.colors.label, textAlign = TextAlign.Center)
        Text(text, style = Subly.type.subhead, color = Subly.colors.secondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(action, onAction, Modifier.width(240.dp))
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, style = Subly.type.caption.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}
