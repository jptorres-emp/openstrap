package org.openstrap.app.presentation

import org.openstrap.app.R
import org.openstrap.app.i18n.Strings
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A moment as the You pages write it (text rules T5): "10:42" today, "Yesterday 10:42", "Tue 10 Nov, 10:42", and with
 * the year when it is from an earlier one, "10 Nov 2025, 10:42". [now] and [epoch] in epoch seconds.
 */
object Stamp {
    fun of(
        epoch: Long, now: Long, zone: ZoneId, use24Hour: Boolean = ClockSetting.use24Hour, locale: Locale = Locale.getDefault(),
        strings: Strings = Strings.app,
    ): String {
        val clock = TimeOfDay.format(epoch, zone, use24Hour, locale)
        val date = Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
        val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
        return when {
            date == today -> clock
            ChronoUnit.DAYS.between(date, today) == 1L -> strings.format(R.string.you_stamp_yesterday, clock)
            date.year == today.year -> strings.format(R.string.you_stamp_dated, DayWords.short(date, locale), clock)
            else -> strings.format(R.string.you_stamp_dated, DayWords.dayMonthYear(date, locale), clock)
        }
    }
}
