package ru.antonshkel.subly.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Модели совпадают с API сервера (server/…/domain/Model.kt). */

@Serializable
enum class Period(val title: String, val short: String) {
    WEEK("Неделя", "нед"), MONTH("Месяц", "мес"), QUARTER("Квартал", "кв"), YEAR("Год", "год");

    val adjective get() = when (this) {
        WEEK -> "Еженедельно"; MONTH -> "Ежемесячно"; QUARTER -> "Раз в квартал"; YEAR -> "Раз в год"
    }
}

@Immutable
@Serializable
data class Subscription(
    val id: Long,
    val name: String,
    val priceKopecks: Long,
    val period: Period,
    val anchorDate: String,
    val nextCharge: String,
    val monthlyKopecks: Long,
    val category: String,
    val color: String,
    val remindDaysBefore: Int,
    val isTrial: Boolean,
    val notes: String = "",
)

@Serializable
data class SubscriptionInput(
    val name: String,
    val priceKopecks: Long,
    val period: Period,
    val anchorDate: String,
    val category: String,
    val color: String,
    val remindDaysBefore: Int,
    val isTrial: Boolean,
    val notes: String,
)

@Immutable
@Serializable
data class User(
    val id: Long,
    val email: String,
    val plan: String,
    val proUntil: String? = null,
    val isAdmin: Boolean = false,
    val subscriptionLimit: Int? = null,
) {
    val isPro get() = plan == "pro"
}

@Serializable data class Credentials(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String, val user: User)
@Serializable data class ErrorDto(val error: String, val message: String)

@Immutable
@Serializable
data class Insight(
    val kind: String,
    val title: String,
    val text: String,
    val savingKopecks: Long? = null,
    val subscriptionIds: List<Long> = emptyList(),
)

@Serializable data class Plan(val code: String, val title: String, val priceKopecks: Long, val months: Long)
@Serializable data class PlansResponse(val plans: List<Plan>, val paymentsAvailable: Boolean, val demo: Boolean)
@Serializable data class CheckoutRequest(val plan: String)
@Serializable data class CheckoutResponse(val confirmationUrl: String? = null, val activated: Boolean = false)

/** Что храним на телефоне: открыть приложение без сети и показать напоминания. */
@Serializable
data class Cache(val user: User? = null, val subscriptions: List<Subscription> = emptyList())

data class Category(val key: String, val title: String)

val CATEGORIES = listOf(
    Category("video", "Видео"), Category("music", "Музыка"), Category("cloud", "Облако"),
    Category("software", "Программы"), Category("games", "Игры"), Category("education", "Учёба"),
    Category("news", "Книги и медиа"), Category("fitness", "Здоровье"), Category("telecom", "Связь"),
    Category("other", "Другое"),
)

fun categoryTitle(key: String) = CATEGORIES.firstOrNull { it.key == key }?.title ?: "Другое"

/** Быстрый старт: популярные сервисы. Цены не подставляем — тарифы у всех разные и часто меняются. */
data class Preset(val name: String, val category: String, val color: String)

val PRESETS = listOf(
    Preset("Яндекс Плюс", "video", "#FF9500"),
    Preset("Кинопоиск", "video", "#FF6A00"),
    Preset("Okko", "video", "#5856D6"),
    Preset("Иви", "video", "#FF2D55"),
    Preset("VK Музыка", "music", "#007AFF"),
    Preset("Звук", "music", "#AF52DE"),
    Preset("YouTube Premium", "video", "#FF3B30"),
    Preset("Telegram Premium", "software", "#30B0C7"),
    Preset("ChatGPT Plus", "software", "#34C759"),
    Preset("Claude Pro", "software", "#D97757"),
    Preset("Яндекс 360", "cloud", "#FFCC00"),
    Preset("iCloud+", "cloud", "#0A84FF"),
    Preset("Литрес", "news", "#FF9500"),
    Preset("Мобильная связь", "telecom", "#8E8E93"),
    Preset("Фитнес-клуб", "fitness", "#34C759"),
)

val PALETTE = listOf("#007AFF", "#34C759", "#FF9500", "#FF3B30", "#AF52DE", "#FF2D55", "#5856D6", "#30B0C7", "#FFCC00", "#8E8E93")
