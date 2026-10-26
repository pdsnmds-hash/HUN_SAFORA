package hu.safora.documentstudio

import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Dates/times are local wall time; WorkManager may deliver later under Doze. */
data class Deadline(
    val id: String, val title: String, val date: String, val done: Boolean = false,
    val time: String = "09:00", val documentId: String = "", val leadMinutes: Long = 1440,
    val repeat: String = "NONE", val anchorDay: Int = 0,
    val revision: String = UUID.randomUUID().toString(),
    val leadDelivered: String = "", val dueDelivered: String = ""
)

object DeadlineRules {
    fun dueAt(deadline: Deadline, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(deadline.date).atTime(LocalTime.parse(deadline.time)).atZone(zone).toInstant().toEpochMilli()
    fun valid(deadline: Deadline): Boolean = runCatching {
        require(deadline.title.isNotBlank())
        dueAt(deadline)
        require(deadline.repeat in listOf("NONE", "DAILY", "WEEKLY", "MONTHLY"))
        require(deadline.leadMinutes in listOf(0L, 60L, 1440L, 10080L))
        true
    }.getOrDefault(false)
    fun nextDate(deadline: Deadline, now: Long, zone: ZoneId = ZoneId.systemDefault()): String? {
        val current = LocalDate.parse(deadline.date)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = LocalTime.parse(deadline.time)
        fun future(date: LocalDate) = date.atTime(time).atZone(zone).toInstant().toEpochMilli() > now
        return when (deadline.repeat) {
            "DAILY", "WEEKLY" -> {
                val step = if (deadline.repeat == "DAILY") 1L else 7L
                val elapsed = ChronoUnit.DAYS.between(current, today).coerceAtLeast(0)
                var candidate = current.plusDays(maxOf(1L, elapsed / step) * step)
                if (!future(candidate)) candidate = candidate.plusDays(step)
                candidate.toString()
            }
            "MONTHLY" -> {
                val anchor = (if (deadline.anchorDay > 0) deadline.anchorDay else current.dayOfMonth).coerceIn(1, 31)
                var month = YearMonth.from(current).plusMonths(maxOf(1L, ChronoUnit.MONTHS.between(YearMonth.from(current), YearMonth.from(today))))
                var candidate = month.atDay(minOf(anchor, month.lengthOfMonth()))
                if (!future(candidate)) { month = month.plusMonths(1); candidate = month.atDay(minOf(anchor, month.lengthOfMonth())) }
                candidate.toString()
            }
            else -> null
        }
    }
}
