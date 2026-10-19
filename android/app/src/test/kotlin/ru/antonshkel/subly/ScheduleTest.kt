package ru.antonshkel.subly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.antonshkel.subly.data.Period
import ru.antonshkel.subly.domain.monthly
import ru.antonshkel.subly.domain.nextCharge
import ru.antonshkel.subly.domain.parseRub
import ru.antonshkel.subly.domain.plural
import ru.antonshkel.subly.domain.rub
import ru.antonshkel.subly.domain.whenText
import java.time.LocalDate

class ScheduleTest {
    private val today = LocalDate.of(2026, 10, 19)

    @Test fun nextChargeMatchesServer() {
        assertEquals(LocalDate.of(2026, 2, 28), nextCharge(LocalDate.of(2026, 1, 31), Period.MONTH, LocalDate.of(2026, 2, 10)))
        assertEquals(LocalDate.of(2026, 10, 21), nextCharge(LocalDate.of(2020, 1, 1), Period.WEEK, today))
        assertEquals(LocalDate.of(2027, 1, 5), nextCharge(LocalDate.of(2024, 1, 5), Period.QUARTER, today))
        assertEquals(today, nextCharge(today, Period.YEAR, today))
    }

    @Test fun monthlyEquivalent() {
        assertEquals(433_33L, monthly(100_00, Period.WEEK))
        assertEquals(99_17L, monthly(1190_00, Period.YEAR))
    }

    @Test fun money() {
        assertEquals("1 190 ₽", rub(1190_00))
        assertEquals("99,90 ₽", rub(99_90))
        assertEquals(399_00L, parseRub("399"))
        assertEquals(399_90L, parseRub("399,9"))
        assertEquals(1_490_00L, parseRub("1 490"))
        assertNull(parseRub("abc"))
        assertNull(parseRub("-5"))
    }

    @Test fun russianPlurals() {
        assertEquals("дня", plural(2, "день", "дня", "дней"))
        assertEquals("дней", plural(11, "день", "дня", "дней"))
        assertEquals("день", plural(21, "день", "дня", "дней"))
        assertEquals("завтра", whenText(1))
        assertEquals("через 5 дней", whenText(5))
    }
}

class SavingTest {
    @Test fun sameSubscriptionIsNotCountedTwice() {
        val list = listOf(
            ru.antonshkel.subly.data.Insight("trial", "", "", 3588_00, listOf(2)),
            ru.antonshkel.subly.data.Insight("duplicate", "", "", 3588_00, listOf(1, 2)),
            ru.antonshkel.subly.data.Insight("duplicate", "", "", 1200_00, listOf(5, 6)),
        )
        assertEquals(4788_00L, ru.antonshkel.subly.ui.screens.potentialSaving(list))
    }
}
