package ru.antonshkel.subly.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Period(val months: Long, val weeks: Long) {
    WEEK(0, 1), MONTH(1, 0), QUARTER(3, 0), YEAR(12, 0);

    /** Дата k-го списания от опорной. Считаем от опорной даты, а не шагами, чтобы 31-е число не «съезжало». */
    fun nth(anchor: LocalDate, k: Long): LocalDate =
        if (weeks > 0) anchor.plusWeeks(weeks * k) else anchor.plusMonths(months * k)

    /** Сколько эта подписка стоит в пересчёте на месяц. */
    fun monthly(price: Long): Long = when (this) {
        WEEK -> Math.round(price * 52 / 12.0)
        MONTH -> price
        QUARTER -> Math.round(price / 3.0)
        YEAR -> Math.round(price / 12.0)
    }

    fun yearly(price: Long): Long = when (this) {
        WEEK -> price * 52
        MONTH -> price * 12
        QUARTER -> price * 4
        YEAR -> price
    }
}

/** Ближайшее списание не раньше today. */
fun nextCharge(anchor: LocalDate, period: Period, today: LocalDate): LocalDate {
    if (!anchor.isBefore(today)) return anchor
    // Грубая оценка числа шагов, потом добиваем вперёд — без длинного цикла для старых дат.
    var k = when {
        period.weeks > 0 -> ChronoUnit.WEEKS.between(anchor, today) / period.weeks
        else -> ChronoUnit.MONTHS.between(anchor, today) / period.months
    }.coerceAtLeast(0)
    var d = period.nth(anchor, k)
    while (d.isBefore(today)) d = period.nth(anchor, ++k)
    return d
}

val CATEGORIES = linkedMapOf(
    "video" to "Видео", "music" to "Музыка", "cloud" to "Облако", "software" to "Программы",
    "games" to "Игры", "education" to "Учёба", "news" to "Книги и медиа", "fitness" to "Здоровье",
    "telecom" to "Связь", "other" to "Другое",
)

@Serializable
data class SubscriptionInput(
    val name: String,
    val priceKopecks: Long,
    val period: Period,
    val anchorDate: String,
    val category: String = "other",
    val color: String = "#007AFF",
    val remindDaysBefore: Int = 1,
    val isTrial: Boolean = false,
    val notes: String = "",
)

@Serializable
data class SubscriptionDto(
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
    val notes: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val plan: String,
    val proUntil: String? = null,
    val isAdmin: Boolean = false,
    val subscriptionLimit: Int? = null,
)

@Serializable data class Credentials(val email: String, val password: String)
@Serializable data class AuthResponse(val token: String, val user: UserDto)
@Serializable data class ErrorDto(val error: String, val message: String)

@Serializable
data class Insight(
    val kind: String,
    val title: String,
    val text: String,
    val savingKopecks: Long? = null,
    val subscriptionIds: List<Long> = emptyList(),
)

@Serializable data class CheckoutRequest(val plan: String)
@Serializable data class CheckoutResponse(val confirmationUrl: String? = null, val activated: Boolean = false)

enum class ProPlan(val code: String, val months: Long, val priceKopecks: Long, val title: String) {
    MONTH("pro_month", 1, 149_00, "Subly Pro на 1 месяц"),
    YEAR("pro_year", 12, 1190_00, "Subly Pro на 12 месяцев");

    companion object { fun of(code: String) = entries.firstOrNull { it.code == code } }
}

const val FREE_LIMIT = 5

class ApiException(val status: Int, val code: String, override val message: String) : RuntimeException(message)

fun badRequest(message: String): Nothing = throw ApiException(400, "bad_request", message)
