package ru.antonshkel.subly

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.antonshkel.subly.data.Insight
import ru.antonshkel.subly.data.Period
import ru.antonshkel.subly.data.Prefs
import ru.antonshkel.subly.data.Subscription
import ru.antonshkel.subly.data.User
import ru.antonshkel.subly.domain.monthly
import ru.antonshkel.subly.ui.MainScaffold
import ru.antonshkel.subly.ui.Tab
import ru.antonshkel.subly.ui.screens.AuthScreen
import ru.antonshkel.subly.ui.screens.EditorScreen
import ru.antonshkel.subly.ui.screens.InsightsScreen
import ru.antonshkel.subly.ui.screens.OverviewScreen
import ru.antonshkel.subly.ui.screens.PaywallScreen
import ru.antonshkel.subly.ui.screens.ProfileScreen
import ru.antonshkel.subly.ui.screens.SubscriptionsScreen
import ru.antonshkel.subly.ui.theme.SublyTheme
import java.time.LocalDate

/**
 * Скриншоты для README. Рендерятся без эмулятора (Robolectric + Roborazzi):
 *   ./gradlew recordRoborazziDebug  →  папка docs/screenshots
 * В обычном прогоне тестов просто проверяют, что экраны рисуются без падений.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi", application = android.app.Application::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 19)

    private fun sub(id: Long, name: String, price: Long, period: Period, inDays: Long, category: String, color: String, trial: Boolean = false) =
        Subscription(id, name, price, period, today.plusDays(inDays).toString(), today.plusDays(inDays).toString(),
            monthly(price, period), category, color, 1, trial)

    private val subs = listOf(
        sub(1, "Яндекс Плюс", 399_00, Period.MONTH, 1, "video", "#FF9500"),
        sub(2, "Кинопоиск", 299_00, Period.MONTH, 3, "video", "#FF6A00", trial = true),
        sub(3, "VK Музыка", 249_00, Period.MONTH, 6, "music", "#007AFF"),
        sub(4, "Claude Pro", 1990_00, Period.MONTH, 12, "software", "#D97757"),
        sub(5, "iCloud+", 1490_00, Period.YEAR, 24, "cloud", "#0A84FF"),
        sub(6, "Мобильная связь", 650_00, Period.MONTH, 9, "telecom", "#8E8E93"),
        sub(7, "Фитнес-клуб", 3500_00, Period.MONTH, 17, "fitness", "#34C759"),
    )
    private val pro = User(1, "anton@antonshkel.ru", "pro", "2027-10-19T10:00:00Z")
    private val free = User(1, "anton@antonshkel.ru", "free", subscriptionLimit = 5)

    private val insights = listOf(
        Insight("total", "86 534 ₽ в год", "Столько стоят ваши 7 подписок. Это 237 ₽ каждый день."),
        Insight("trial", "Пробный период «Кинопоиск» скоро закончится", "Через 3 дня (22.10) начнут списывать 299 ₽. Если сервис не нужен, отмените подписку заранее.", 3588_00, listOf(2)),
        Insight("duplicate", "2 сервиса в категории «Видео»", "Яндекс Плюс, Кинопоиск. Если оставить один, сэкономите от 3 588 ₽ в год.", 3588_00, listOf(1, 2)),
        Insight("upcoming", "За 7 дней спишется 947 ₽", "Яндекс Плюс — 20.10, Кинопоиск — 22.10, VK Музыка — 25.10."),
    )

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SublyTheme(dark) { content() } }
        compose.mainClock.advanceTimeBy(2_000)       // даём досчитаться суммам и диаграмме
        compose.onRoot().captureRoboImage("../../docs/screenshots/$name.png")
    }

    @Test fun overview() = shot("01_overview") {
        MainScaffold(Tab.OVERVIEW, {}) { OverviewScreen(pro, subs, false, it, {}, {}, {}, today) }
    }

    @Test fun list() = shot("02_subscriptions") {
        MainScaffold(Tab.LIST, {}) { SubscriptionsScreen(pro, subs, it, {}, {}, {}, today) }
    }

    @Test fun editor() = shot("03_editor") {
        EditorScreen(subs[0], busy = false, onCancel = {}, onSave = {}, onDelete = {}, today = today)
    }

    @Test fun insightsScreen() = shot("04_insights") {
        MainScaffold(Tab.INSIGHTS, {}) { InsightsScreen(pro, insights, null, subs.size, it, {}, {}) }
    }

    @Test fun paywall() = shot("05_paywall") {
        PaywallScreen(plans = null, busy = false, isPro = false, onClose = {}, onBuy = {})
    }

    @Test fun overviewDark() = shot("06_overview_dark", dark = true) {
        MainScaffold(Tab.OVERVIEW, {}) { OverviewScreen(pro, subs, false, it, {}, {}, {}, today) }
    }

    @Test fun auth() = shot("07_auth") {
        AuthScreen(busy = false, error = null, serverUrl = "https://subly.antonshkel.ru", onSubmit = { _, _, _ -> }, onServer = {}, onPrivacy = {})
    }

    @Test fun profile() = shot("08_profile") {
        MainScaffold(Tab.PROFILE, {}) {
            ProfileScreen(free, Prefs(token = "x"), it, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
