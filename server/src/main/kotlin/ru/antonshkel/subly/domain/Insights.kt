package ru.antonshkel.subly.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY = DateTimeFormatter.ofPattern("dd.MM")
private val STREAMING = setOf("video", "music", "cloud", "news", "games")

fun rub(kopecks: Long): String {
    val rubles = kopecks / 100
    val grouped = rubles.toString().reversed().chunked(3).joinToString(" ").reversed()
    val rest = kopecks % 100
    return if (rest == 0L) "$grouped ₽" else "$grouped,${rest.toString().padStart(2, '0')} ₽"
}

private fun days(n: Long) = when {
    n % 10 == 1L && n % 100 != 11L -> "$n день"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n дня"
    else -> "$n дней"
}

/**
 * Советы по набору подписок. Только правила, без «магии»: каждый совет можно проверить руками,
 * а сумма экономии считается консервативно.
 */
fun buildInsights(subs: List<SubscriptionDto>, today: LocalDate): List<Insight> {
    if (subs.isEmpty()) return emptyList()
    val out = mutableListOf<Insight>()
    val yearly = subs.sumOf { it.period.yearly(it.priceKopecks) }

    out += Insight(
        kind = "total", title = "${rub(yearly)} в год",
        text = "Столько стоят ваши ${subs.size} подписк${if (subs.size == 1) "а" else "и"}. Это ${rub(yearly / 365)} каждый день.",
    )

    subs.filter { it.isTrial }.forEach { s ->
        val left = ChronoUnit.DAYS.between(today, LocalDate.parse(s.nextCharge))
        if (left in 0..7) out += Insight(
            kind = "trial", title = "Пробный период «${s.name}» скоро закончится",
            text = "Через ${days(left)} (${LocalDate.parse(s.nextCharge).format(DAY)}) начнут списывать ${rub(s.priceKopecks)}. " +
                "Если сервис не нужен, отмените подписку заранее.",
            savingKopecks = s.period.yearly(s.priceKopecks), subscriptionIds = listOf(s.id),
        )
    }

    subs.filter { it.category != "other" }.groupBy { it.category }.filterValues { it.size >= 2 }.forEach { (cat, group) ->
        val cheapest = group.minBy { it.period.yearly(it.priceKopecks) }
        out += Insight(
            kind = "duplicate", title = "${group.size} сервиса в категории «${CATEGORIES[cat] ?: cat}»",
            text = "${group.joinToString(", ") { it.name }}. Если оставить один, сэкономите от ${rub(cheapest.period.yearly(cheapest.priceKopecks))} в год.",
            savingKopecks = cheapest.period.yearly(cheapest.priceKopecks), subscriptionIds = group.map { it.id },
        )
    }

    subs.filter { it.period == Period.WEEK }.forEach { s ->
        out += Insight(
            kind = "weekly", title = "«${s.name}» списывается каждую неделю",
            text = "За год это ${rub(s.period.yearly(s.priceKopecks))}. Проверьте, нет ли тарифа на месяц или год: обычно он дешевле.",
            subscriptionIds = listOf(s.id),
        )
    }

    if (subs.size >= 3 && yearly > 0) {
        val top = subs.maxBy { it.period.yearly(it.priceKopecks) }
        val share = top.period.yearly(top.priceKopecks) * 100 / yearly
        if (share >= 40) out += Insight(
            kind = "share", title = "«${top.name}» — $share% всех трат",
            text = "Одна подписка забирает почти половину бюджета на сервисы. Посмотрите, нужен ли вам такой тариф.",
            subscriptionIds = listOf(top.id),
        )
    }

    val week = subs.filter { ChronoUnit.DAYS.between(today, LocalDate.parse(it.nextCharge)) in 0..6 }
    if (week.isNotEmpty()) out += Insight(
        kind = "upcoming", title = "За 7 дней спишется ${rub(week.sumOf { it.priceKopecks })}",
        text = week.sortedBy { it.nextCharge }.joinToString(", ") { "${it.name} — ${LocalDate.parse(it.nextCharge).format(DAY)}" } + ".",
        subscriptionIds = week.map { it.id },
    )

    val streaming = subs.filter { it.category in STREAMING }.sumOf { it.period.yearly(it.priceKopecks) }
    if (streaming > 0 && streaming * 100 / yearly >= 60 && subs.size >= 3) out += Insight(
        kind = "entertainment", title = "Развлечения — ${streaming * 100 / yearly}% бюджета",
        text = "Кино, музыка и игры обходятся в ${rub(streaming)} в год. Многие сервисы продают семейные тарифы: одна подписка на несколько человек выходит дешевле.",
    )
    return out
}
