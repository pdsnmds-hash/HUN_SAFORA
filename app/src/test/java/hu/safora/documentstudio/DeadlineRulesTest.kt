package hu.safora.documentstudio

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DeadlineRulesTest {
    private val zone = ZoneId.of("Europe/Budapest")
    private fun at(value: String) = LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    @Test fun dailySkipsMissedDaysWithoutReplayingEveryNotification() {
        val d = Deadline("1", "Payment", "2026-01-01", time = "09:00", repeat = "DAILY")
        assertEquals("2026-10-25", DeadlineRules.nextDate(d, at("2026-10-24T11:00"), zone))
    }
    @Test fun weeklyKeepsOriginalWeekday() {
        val d = Deadline("1", "Payment", "2026-01-01", repeat = "WEEKLY")
        assertEquals("2026-01-22", DeadlineRules.nextDate(d, at("2026-01-15T09:00"), zone))
    }
    @Test fun monthlyPreservesThirtyFirstAcrossFebruary() {
        val january = Deadline("1", "Payment", "2028-01-31", repeat = "MONTHLY", anchorDay = 31)
        assertEquals("2028-02-29", DeadlineRules.nextDate(january, at("2028-01-31T09:00"), zone))
        assertEquals("2028-03-31", DeadlineRules.nextDate(january.copy(date = "2028-02-29"), at("2028-02-29T09:00"), zone))
    }
    @Test fun monthlySkipsYearsWithoutDrifting() {
        val d = Deadline("1", "Payment", "2020-01-31", repeat = "MONTHLY", anchorDay = 31)
        assertEquals("2026-11-30", DeadlineRules.nextDate(d, at("2026-10-31T10:00"), zone))
    }
    @Test fun dailyUsesWallTimeAcrossDaylightSaving() {
        val d = Deadline("1", "Payment", "2026-03-28", time = "09:00", repeat = "DAILY")
        val next = d.copy(date = DeadlineRules.nextDate(d, at("2026-03-28T09:00"), zone)!!)
        assertEquals(23 * 3600_000L, DeadlineRules.dueAt(next, zone) - DeadlineRules.dueAt(d, zone))
    }
    @Test fun rejectsInvalidDatesAndOptions() {
        assertFalse(DeadlineRules.valid(Deadline("1", "Date", "2026-02-30")))
        assertFalse(DeadlineRules.valid(Deadline("1", "Date", "2026-02-28", time = "25:10")))
        assertFalse(DeadlineRules.valid(Deadline("1", "Date", "2026-02-28", repeat = "YEARLY")))
        assertFalse(DeadlineRules.valid(Deadline("1", "", "2026-02-28")))
    }
    @Test fun oneShotHasNoNextOccurrence() {
        assertNull(DeadlineRules.nextDate(Deadline("1", "Date", "2026-01-01"), at("2026-01-01T10:00"), zone))
    }
    @Test fun searchFindsHungarianTextWithoutDiacritics() {
        val d = Doc("1", "scan.pdf", "", "", "application/pdf", 0, 0, text = "Fizetési határidő: október")
        assertTrue(d.matches("hatarido"))
        assertTrue(d.matches("OKTOBER"))
        assertFalse(d.matches("november"))
    }
}
