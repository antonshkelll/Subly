package ru.antonshkel.subly

import ru.antonshkel.subly.domain.Period
import ru.antonshkel.subly.domain.nextCharge
import ru.antonshkel.subly.domain.rub
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainTest {
    @Test
    fun `next charge keeps the day of month`() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), nextCharge(anchor, Period.MONTH, LocalDate.of(2026, 2, 10)))
        assertEquals(LocalDate.of(2026, 3, 31), nextCharge(anchor, Period.MONTH, LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `next charge for future, today and old anchors`() {
        val today = LocalDate.of(2026, 10, 19)
        assertEquals(LocalDate.of(2026, 11, 1), nextCharge(LocalDate.of(2026, 11, 1), Period.MONTH, today))
        assertEquals(today, nextCharge(today, Period.YEAR, today))
        assertEquals(LocalDate.of(2026, 10, 21), nextCharge(LocalDate.of(2020, 1, 1), Period.WEEK, today))
        assertEquals(LocalDate.of(2027, 1, 5), nextCharge(LocalDate.of(2024, 1, 5), Period.QUARTER, today))
        assertEquals(LocalDate.of(2027, 3, 1), nextCharge(LocalDate.of(2015, 3, 1), Period.YEAR, today))
    }

    @Test
    fun `monthly and yearly equivalents`() {
        assertEquals(433_33, Period.WEEK.monthly(100_00))
        assertEquals(100_00, Period.QUARTER.monthly(300_00))
        assertEquals(5200_00, Period.WEEK.yearly(100_00))
    }

    @Test
    fun `money formatting`() {
        assertEquals("1 490 ₽", rub(1490_00))
        assertEquals("99,90 ₽", rub(99_90))
        assertEquals("0 ₽", rub(0))
    }
}
