package com.example.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Formatters {

    private val pkrIntegerFormat = DecimalFormat("#,##0")
    private val pkrDecimalFormat = DecimalFormat("#,##0.00")

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val isoMonthFormat = SimpleDateFormat("yyyy-MM", Locale.US)
    private val timeDisplayFormat = SimpleDateFormat("hh:mm a", Locale.US)
    private val fullDateDisplayFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val dayOfWeekDateDisplayFormat = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US)
    private val monthYearDisplayFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val shortDayMonthFormat = SimpleDateFormat("MMM d", Locale.US)

    /**
     * Formats PKR currency, e.g.:
     * Rs. 50
     * Rs. 1,250
     * Rs. 18,450
     */
    fun formatPkr(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            "Rs. ${pkrIntegerFormat.format(amount.toLong())}"
        } else {
            "Rs. ${pkrDecimalFormat.format(amount)}"
        }
    }

    /**
     * Returns today's date formatted as YYYY-MM-DD
     */
    fun todayDateString(): String {
        return isoDateFormat.format(Date())
    }

    /**
     * Returns current month formatted as YYYY-MM
     */
    fun currentMonthString(): String {
        return isoMonthFormat.format(Date())
    }

    /**
     * Returns current time formatted as hh:mm a (e.g. "02:30 PM")
     */
    fun currentTimeString(): String {
        return timeDisplayFormat.format(Date())
    }

    /**
     * Converts "2026-10-05" to "October 5, 2026"
     */
    fun formatDateFull(dateString: String): String {
        return try {
            val date = isoDateFormat.parse(dateString) ?: return dateString
            fullDateDisplayFormat.format(date)
        } catch (_: Exception) {
            dateString
        }
    }

    /**
     * Converts "2026-10-05" to "Monday, Oct 5, 2026"
     */
    fun formatDateWithDay(dateString: String): String {
        return try {
            val date = isoDateFormat.parse(dateString) ?: return dateString
            dayOfWeekDateDisplayFormat.format(date)
        } catch (_: Exception) {
            dateString
        }
    }

    /**
     * Converts "2026-10-05" to "Oct 5"
     */
    fun formatDayMonth(dateString: String): String {
        return try {
            val date = isoDateFormat.parse(dateString) ?: return dateString
            shortDayMonthFormat.format(date)
        } catch (_: Exception) {
            dateString
        }
    }

    /**
     * Converts "2026-10" to "October 2026"
     */
    fun formatMonthYear(monthString: String): String {
        return try {
            val date = isoMonthFormat.parse(monthString) ?: return monthString
            monthYearDisplayFormat.format(date)
        } catch (_: Exception) {
            monthString
        }
    }

    /**
     * Move month back by 1 (e.g. "2026-10" -> "2026-09")
     */
    fun getPreviousMonth(monthString: String): String {
        return try {
            val date = isoMonthFormat.parse(monthString) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.MONTH, -1)
            isoMonthFormat.format(cal.time)
        } catch (_: Exception) {
            monthString
        }
    }

    /**
     * Move month forward by 1 (e.g. "2026-10" -> "2026-11")
     */
    fun getNextMonth(monthString: String): String {
        return try {
            val date = isoMonthFormat.parse(monthString) ?: Date()
            val cal = Calendar.getInstance()
            cal.time = date
            cal.add(Calendar.MONTH, 1)
            isoMonthFormat.format(cal.time)
        } catch (_: Exception) {
            monthString
        }
    }

    data class CalendarDay(
        val dayNumber: Int,
        val dateString: String,
        val isCurrentMonth: Boolean,
        val isToday: Boolean
    )

    /**
     * Generates a 6-week or 5-week grid of CalendarDay objects for a month like "2026-10"
     */
    fun generateCalendarDays(monthString: String): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()
        val todayStr = todayDateString()

        val cal = Calendar.getInstance()
        val parsedDate = try {
            isoMonthFormat.parse(monthString) ?: Date()
        } catch (_: Exception) {
            Date()
        }
        cal.time = parsedDate
        cal.set(Calendar.DAY_OF_MONTH, 1)

        val targetYear = cal.get(Calendar.YEAR)
        val targetMonth = cal.get(Calendar.MONTH) // 0-indexed
        val maxDaysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Day of week of the 1st day (Sunday = 1, Monday = 2, ...)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 (Sun) to 7 (Sat)
        val leadingDaysCount = firstDayOfWeek - 1 // Days from previous month to show

        // Previous month filler
        val prevCal = Calendar.getInstance()
        prevCal.time = cal.time
        prevCal.add(Calendar.MONTH, -1)
        val maxDaysInPrevMonth = prevCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (i in (maxDaysInPrevMonth - leadingDaysCount + 1)..maxDaysInPrevMonth) {
            prevCal.set(Calendar.DAY_OF_MONTH, i)
            val dateStr = isoDateFormat.format(prevCal.time)
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateString = dateStr,
                    isCurrentMonth = false,
                    isToday = dateStr == todayStr
                )
            )
        }

        // Current month days
        for (i in 1..maxDaysInCurrentMonth) {
            cal.set(Calendar.DAY_OF_MONTH, i)
            val dateStr = isoDateFormat.format(cal.time)
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateString = dateStr,
                    isCurrentMonth = true,
                    isToday = dateStr == todayStr
                )
            )
        }

        // Next month trailing days to complete full weeks (up to 35 or 42 cells)
        val totalCells = if (days.size <= 35) 35 else 42
        val trailingDaysCount = totalCells - days.size
        val nextCal = Calendar.getInstance()
        nextCal.time = cal.time
        nextCal.add(Calendar.MONTH, 1)
        for (i in 1..trailingDaysCount) {
            nextCal.set(Calendar.DAY_OF_MONTH, i)
            val dateStr = isoDateFormat.format(nextCal.time)
            days.add(
                CalendarDay(
                    dayNumber = i,
                    dateString = dateStr,
                    isCurrentMonth = false,
                    isToday = dateStr == todayStr
                )
            )
        }

        return days
    }
}
