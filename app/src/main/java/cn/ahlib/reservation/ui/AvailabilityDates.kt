package cn.ahlib.reservation.ui

import cn.ahlib.reservation.data.AvailabilityDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun completeAvailabilityDates(
    availability: List<AvailabilityDay>,
    today: LocalDate = LocalDate.now(),
): List<AvailabilityDay> {
    val daysByDate = linkedMapOf<LocalDate, AvailabilityDay>()
    availability.forEach { day ->
        val sourceDate = day.date
            .takeIf(String::isNotBlank)
            ?: day.bookDate?.takeIf(String::isNotBlank)
        val date = sourceDate?.toLocalDateOrNull() ?: return@forEach
        daysByDate.putIfAbsent(
            date,
            day.copy(
                date = date.toString(),
                bookDate = day.bookDate?.takeIf(String::isNotBlank)
                    ?: date.toString(),
            ),
        )
    }
    if (daysByDate.isEmpty()) {
        return emptyList()
    }

    val visibleDaysByDate = daysByDate
        .filterKeys { date -> !date.isBefore(today) }
    val lastDate = maxOf(
        visibleDaysByDate.keys.maxOrNull() ?: today,
        today,
    )
    val rangeDays = ChronoUnit.DAYS.between(today, lastDate)
    val completed = if (rangeDays <= MAX_COMPLETED_DATE_RANGE_DAYS) {
        val calendarEnd = lastDate.plusDays(
            (
                LAST_DAY_OF_WEEK_INDEX -
                    lastDate.dayOfWeek.value % DAYS_PER_WEEK +
                    DAYS_PER_WEEK
            ) % DAYS_PER_WEEK.toLong(),
        )
        generateSequence(today) { date ->
            date.plusDays(1).takeIf { next -> !next.isAfter(calendarEnd) }
        }.map { date ->
            visibleDaysByDate[date] ?: closedAvailabilityDay(date)
        }.toList()
    } else {
        buildList {
            add(visibleDaysByDate[today] ?: closedAvailabilityDay(today))
            addAll(
                visibleDaysByDate
                    .toSortedMap()
                    .filterKeys { date -> date != today }
                    .values,
            )
        }
    }
    return completed
}

private fun closedAvailabilityDay(date: LocalDate): AvailabilityDay =
    AvailabilityDay(
        date = date.toString(),
        bookDate = date.toString(),
        list = emptyList(),
        isOpen = 0,
        totalLeftNum = 0,
    )

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching {
        LocalDate.parse(substringBefore('T').substringBefore(' '))
    }.getOrNull()

private const val MAX_COMPLETED_DATE_RANGE_DAYS = 62L
private const val DAYS_PER_WEEK = 7
private const val LAST_DAY_OF_WEEK_INDEX = 6
