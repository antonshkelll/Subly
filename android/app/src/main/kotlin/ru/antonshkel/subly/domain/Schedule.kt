package ru.antonshkel.subly.domain

import ru.antonshkel.subly.data.Period
import ru.antonshkel.subly.data.Subscription
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Та же логика, что на сервере: k-е списание считаем от опорной даты, чтобы 31-е не «съезжало». */
fun nth(anchor: LocalDate, period: Period, k: Long): LocalDate = when (period) {
    Period.WEEK -> anchor.plusWeeks(k)
    Period.MONTH -> anchor.plusMonths(k)
    Period.QUARTER -> anchor.plusMonths(3 * k)
    Period.YEAR -> anchor.plusYears(k)
}

fun nextCharge(anchor: LocalDate, period: Period, today: LocalDate): LocalDate {
    if (!anchor.isBefore(today)) return anchor
    var k = when (period) {
        Period.WEEK -> ChronoUnit.WEEKS.between(anchor, today)
        Period.MONTH -> ChronoUnit.MONTHS.between(anchor, today)
        Period.QUARTER -> ChronoUnit.MONTHS.between(anchor, today) / 3
        Period.YEAR -> ChronoUnit.YEARS.between(anchor, today)
    }.coerceAtLeast(0)
    var d = nth(anchor, period, k)
    while (d.isBefore(today)) d = nth(anchor, period, ++k)
    return d
}

fun monthly(price: Long, period: Period): Long = when (period) {
    Period.WEEK -> Math.round(price * 52 / 12.0)
    Period.MONTH -> price
    Period.QUARTER -> Math.round(price / 3.0)
    Period.YEAR -> Math.round(price / 12.0)
}

/** Дата следующего списания на сегодня — кеш мог устареть, если приложение долго не открывали. */
fun Subscription.next(today: LocalDate = LocalDate.now()): LocalDate =
    nextCharge(LocalDate.parse(anchorDate), period, today)

fun Subscription.daysLeft(today: LocalDate = LocalDate.now()): Long = ChronoUnit.DAYS.between(today, next(today))

private val RU = Locale("ru")
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", RU)
private val DAY_MONTH_SHORT = DateTimeFormatter.ofPattern("d MMM", RU)
private val FULL = DateTimeFormatter.ofPattern("EEEE, d MMMM", RU)
private val DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)

fun LocalDate.dayMonth(): String = format(DAY_MONTH)
fun LocalDate.dayMonthShort(): String = format(DAY_MONTH_SHORT).trimEnd('.')
fun LocalDate.fullDate(): String = format(FULL).replaceFirstChar { it.uppercase() }
fun LocalDate.longDate(): String = format(DATE)

fun plural(n: Long, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when {
        m10 == 1L && m100 != 11L -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
}

fun whenText(days: Long): String = when (days) {
    0L -> "сегодня"
    1L -> "завтра"
    else -> "через $days ${plural(days, "день", "дня", "дней")}"
}

/** 1490_00 → «1 490 ₽», 99_90 → «99,90 ₽». Неразрывные пробелы, чтобы сумма не переносилась. */
fun rub(kopecks: Long, withSign: Boolean = true): String {
    val neg = kopecks < 0
    val abs = kotlin.math.abs(kopecks)
    val rubles = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val rest = abs % 100
    val body = if (rest == 0L) rubles else "$rubles,${rest.toString().padStart(2, '0')}"
    return (if (neg) "−" else "") + body + if (withSign) " ₽" else ""
}

/** «399», «399,9», «399.90» → копейки. null — если это не число. */
fun parseRub(text: String): Long? {
    val t = text.replace(" ", "").replace(" ", "").replace(',', '.').trim()
    if (t.isEmpty()) return null
    val v = t.toBigDecimalOrNull() ?: return null
    if (v.signum() < 0 || v > java.math.BigDecimal(1_000_000)) return null
    return v.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Для итогов и пересчётов копейки — шум: «7 211 ₽» читается лучше, чем «7 211,17 ₽». */
fun rubRound(kopecks: Long): String = rub(Math.round(kopecks / 100.0) * 100)

fun kopecksToInput(k: Long): String = if (k % 100 == 0L) (k / 100).toString() else "%d,%02d".format(k / 100, k % 100)
