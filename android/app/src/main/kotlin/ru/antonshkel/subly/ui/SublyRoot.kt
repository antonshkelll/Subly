package ru.antonshkel.subly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.ui.components.Banner
import ru.antonshkel.subly.ui.screens.AuthScreen
import ru.antonshkel.subly.ui.screens.EditorScreen
import ru.antonshkel.subly.ui.screens.InsightsScreen
import ru.antonshkel.subly.ui.screens.OnboardingScreen
import ru.antonshkel.subly.ui.screens.OverviewScreen
import ru.antonshkel.subly.ui.screens.PaywallScreen
import ru.antonshkel.subly.ui.screens.ProfileScreen
import ru.antonshkel.subly.ui.screens.ServerDialog
import ru.antonshkel.subly.ui.screens.SubscriptionsScreen
import ru.antonshkel.subly.ui.theme.Subly

enum class Tab(val title: String, val icon: ImageVector) {
    OVERVIEW("Обзор", Icons.Rounded.Home),
    LIST("Подписки", Icons.Rounded.List),
    INSIGHTS("Советы", Icons.Rounded.Star),
    PROFILE("Профиль", Icons.Rounded.Person),
}

/** Что открыто поверх вкладок. Свой маленький «навигатор» вместо библиотеки — четыре экрана, два модальных окна. */
private sealed interface Overlay {
    data class Editor(val subscription: Subscription?) : Overlay
    data object Paywall : Overlay
}

@Composable
fun SublyRoot(state: UiState, vm: AppViewModel, openUrl: (String) -> Unit) {
    Box(Modifier.fillMaxSize().background(Subly.colors.background)) {
        AnimatedContent(state.phase, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "phase") { phase ->
            when (phase) {
                Phase.LOADING -> Box(Modifier.fillMaxSize())
                Phase.ONBOARDING -> OnboardingScreen(onDone = { vm.finishOnboarding() })
                Phase.AUTH -> AuthScreen(
                    busy = state.busy, error = state.authError, serverUrl = state.prefs.serverUrl,
                    onSubmit = { e, p, r -> vm.auth(e, p, r) }, onServer = { vm.setServer(it) },
                    onPrivacy = { openUrl(state.prefs.serverUrl + "/privacy") },
                )
                Phase.MAIN -> Main(state, vm, openUrl)
            }
        }
        Banner(state.message, Modifier.statusBarsPadding().align(Alignment.TopCenter))
    }
}

@Composable
private fun Main(state: UiState, vm: AppViewModel, openUrl: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var serverDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = overlay != null || tab != 0) {
        if (overlay != null) overlay = null else tab = 0
    }

    val openAdd = {
        val limit = state.user?.subscriptionLimit
        overlay = if (limit != null && state.subscriptions.size >= limit) { vm.loadPlans(); Overlay.Paywall } else Overlay.Editor(null)
    }
    val openPaywall = { vm.loadPlans(); overlay = Overlay.Paywall }

    MainScaffold(Tab.entries[tab], onTab = { tab = it.ordinal; if (it == Tab.INSIGHTS && state.user?.isPro == true && state.insights == null) vm.loadInsights() }) { padding ->
        when (Tab.entries[tab]) {
            Tab.OVERVIEW -> OverviewScreen(state.user, state.subscriptions, state.refreshing, padding, { vm.refresh() }, openAdd, { overlay = Overlay.Editor(it) })
            Tab.LIST -> SubscriptionsScreen(state.user, state.subscriptions, padding, openAdd, { overlay = Overlay.Editor(it) }, openPaywall)
            Tab.INSIGHTS -> InsightsScreen(state.user, state.insights, state.insightsError, state.subscriptions.size, padding, openPaywall) { vm.loadInsights() }
            Tab.PROFILE -> ProfileScreen(
                user = state.user, prefs = state.prefs, contentPadding = padding,
                onUpgrade = openPaywall, onTheme = vm::setTheme, onReminders = vm::setReminders,
                onExport = vm::exportCsv, onRestore = { vm.checkBilling(force = true) }, onServer = { serverDialog = true },
                onOpenUrl = openUrl, onLogout = { vm.logout() }, onDeleteAccount = { vm.deleteAccount() },
            )
        }
    }

    // Модальные экраны выезжают снизу, как в iOS
    AnimatedVisibility(
        visible = overlay != null,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200)),
    ) {
        // Запоминаем последний оверлей, чтобы он не исчезал раньше конца анимации закрытия.
        var last by remember { mutableStateOf(overlay) }
        if (overlay != null) last = overlay
        when (val o = last) {
            is Overlay.Editor -> EditorScreen(
                initial = o.subscription, busy = state.busy,
                onCancel = { overlay = null },
                onSave = { input -> vm.save(o.subscription?.id, input) { overlay = null } },
                onDelete = { o.subscription?.let { s -> vm.delete(s.id) { overlay = null } } },
            )
            Overlay.Paywall -> PaywallScreen(
                plans = state.plans, busy = state.busy, isPro = state.user?.isPro == true,
                onClose = { overlay = null }, onBuy = { vm.checkout(it) {} },
            )
            null -> {}
        }
    }

    if (serverDialog) ServerDialog(state.prefs.serverUrl, onDismiss = { serverDialog = false }, onSave = { serverDialog = false; vm.setServer(it) })
}

/** Каркас вкладок: контент + нижняя панель. Отдельно — чтобы использовать и в скриншот-тестах. */
@Composable
fun MainScaffold(tab: Tab, onTab: (Tab) -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().background(Subly.colors.background)) {
        content(PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + 84.dp))
        TabBar(tab, onTab, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TabBar(selected: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier) {
    // Полупрозрачная панель без blur: размытие дорогое и есть только на Android 12+.
    Column(modifier.fillMaxWidth().background(Subly.colors.card.copy(alpha = if (Subly.colors.isDark) 0.96f else 0.97f))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Subly.colors.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp)) {
            Tab.entries.forEach { t ->
                val active = t == selected
                val color = if (active) Subly.colors.accent else Subly.colors.tertiary
                Column(
                    Modifier.weight(1f).fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onTab(t) }.padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, t.title, tint = color, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(t.title, style = Subly.type.caption, color = color)
                }
            }
        }
    }
}
