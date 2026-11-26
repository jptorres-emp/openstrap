package org.openstrap.app.presentation

import androidx.annotation.StringRes
import org.openstrap.app.R
import org.openstrap.app.data.settings.UnitSetting
import org.openstrap.app.data.settings.UnitSystem
import org.openstrap.app.i18n.Strings
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * How the app writes a unit, one style each, as decided at the wave 3 design gate
 * (docs/audits/2026-11-02-wave3-format-choices.md, all ten recommendations approved on 2026-11-02):
 *
 * 1. Distance. A workout shows 2 decimals ("5.21 km"), a day's total 1 ("5.2 km"). Under 1 km it's metres ("820 m"),
 *    under 10 m nothing. The decimal mark follows the phone's language, except in the dot font, which keeps the dot.
 *    Distance from steps always gets "≈ " in front. The profile's Imperial choice writes the same in miles and feet
 *    ("3.11 mi", "3.1 mi", feet under a tenth of a mile, "528 ft"): every function here reads [UnitSetting] for it, so a
 *    call site never says which system. Stored distances, GPX and the Health Connect export stay metric.
 * 2. Duration. "7 h 40" everywhere, "7 h" on the hour and "40 min" under an hour, never a clock look ("7:40").
 * 3. Workout timer. "12:34", then "1:02:03" past the hour, written once, while it runs. A finished workout's length is
 *    "45 s", "1 min 22" or "1 h 02" ([Units.length]).
 * 4. Time of day. Every clock follows the phone's 12 or 24 hour setting through [TimeOfDay], widgets and the
 *    notification too.
 * 5. Pace. "5:24/km" ("8:40/mi" in miles) everywhere, shown once a workout has covered 100 m, and never slower than
 *    60 min a km ([Pace]). Speed, where it shows, is km/h or mph ([speed]).
 * 6. Day words. "Today", "Yesterday", the weekday for the rest of the last 7 days, then "Tue 21 Oct", with the year
 *    from an earlier year. Lowercase after other words ("yesterday 10:42"). Charts keep "Tue 21 Oct" ([DayWords]).
 * 7. Week start. The phone's locale decides the first day of every week, in one place ([org.openstrap.app.data.Weeks]).
 * 8. Score bands. Recovery 85 and 70, sleep 90, 80 and 60, words unchanged, defined once. Load follows Today's rule
 *    ([LoadBands]).
 * 9. Valid heart rate. 30 to 230 bpm everywhere, database queries and workouts included
 *    ([org.openstrap.app.data.HeartRate]).
 * 10. Strap out of date. One threshold, 3 hours, and one low battery level, 15% ([StrapBands]).
 *
 * Every function takes the locale it writes in, the phone's by default. Text drawn in the dot font passes [DOT_LOCALE]
 * or uses [dotFormat], since the font has only ASCII digits and both marks but no other script's digits.
 */
// One entry point for every number a person reads, so each format has one home and one rule.
@Suppress("TooManyFunctions")
object Units {
    /** The locale for text drawn in the dot font: ASCII digits and a dot decimal mark. */
    val DOT_LOCALE: Locale = Locale.ROOT

    /** In front of a distance worked out from steps rather than measured. */
    const val FROM_STEPS = "≈ "

    /** A distance under this many metres shows nothing: too little to mean anything. */
    const val SHOWN_FROM_METERS = 10f

    /** Metres in a kilometre, where a metric distance turns from metres to km. */
    const val METERS_PER_KM = 1000f

    /** Metres in a mile. */
    const val METERS_PER_MILE = 1609.344f

    private const val FEET_PER_METER = 3.280839895f

    /** In miles a distance turns from feet to miles at a tenth of one. */
    private const val MILE_TENTHS = 10

    /** The system distances are written in now, from the profile. */
    val system: UnitSystem get() = UnitSetting.system

    /** Whether distances are written in miles and feet. */
    val isImperial: Boolean get() = system == UnitSystem.Imperial

    /** Metres in the unit a long distance is counted in: a kilometre or a mile. */
    val majorMeters: Float get() = if (isImperial) METERS_PER_MILE else METERS_PER_KM

    /** Below this many metres a distance is written in the small unit (metres, or feet under a tenth of a mile). */
    val minorBelowMeters: Float get() = if (isImperial) METERS_PER_MILE / MILE_TENTHS else METERS_PER_KM

    /** Whether [meters] is written in km or miles rather than metres or feet. */
    fun isMajor(meters: Float): Boolean = meters >= minorBelowMeters

    /** Metres in one split: a kilometre or a mile. */
    val splitMeters: Double get() = system.splitMeters

    /**
     * The label for a unit, one place: "km" or "mi", "m" or "ft", "/km" or "/mi", "km/h" or "mph". Call sites that draw
     * the unit apart from its number ask here, none writes "km" itself.
     */
    fun majorUnit(strings: Strings = Strings.app): String =
        strings.get(if (isImperial) R.string.core_unit_label_mi else R.string.core_unit_label_km)

    fun minorUnit(strings: Strings = Strings.app): String =
        strings.get(if (isImperial) R.string.core_unit_label_ft else R.string.core_unit_label_m)

    fun paceUnit(strings: Strings = Strings.app): String =
        strings.get(if (isImperial) R.string.core_unit_label_per_mi else R.string.core_unit_label_per_km)

    fun speedUnit(strings: Strings = Strings.app): String =
        strings.get(if (isImperial) R.string.core_unit_label_mph else R.string.core_unit_label_kmh)

    /** The string for the system in force: [metric] in km, [miles] in miles, for a sentence with its unit inside. */
    @StringRes
    fun res(@StringRes metric: Int, @StringRes miles: Int): Int = if (isImperial) miles else metric

    /** A distance as a number and its unit apart, "5.21" and "km" or "820" and "m". */
    data class Figure(val number: String, val unit: String)

    internal const val MINUTE = 60L
    private const val HOUR = 3600L
    private const val MINUTES_PER_HOUR = 60L

    /** How many decimals a distance in km takes: a workout's own, or a whole day's total. */
    enum class Precision(val decimals: Int) {
        /** The live screen, summary, notification and share card: "5.21 km". */
        Workout(2),

        /** Today, Activity and the widgets: "5.2 km". */
        Day(1),
    }

    /**
     * [meters] with its unit: "5.21 km" or "5.2 km" by [precision], "820 m" under a kilometre, with "≈ " in front when
     * [fromSteps]. In miles "3.11 mi" and "3.1 mi", and "528 ft" under a tenth of a mile. Null under [SHOWN_FROM_METERS].
     */
    fun distance(
        meters: Float, precision: Precision, fromSteps: Boolean = false, locale: Locale = Locale.getDefault(),
        strings: Strings = Strings.app,
    ): String? {
        if (meters < SHOWN_FROM_METERS) return null
        val text = if (isMajor(meters)) {
            strings.format(if (isImperial) R.string.core_unit_mi else R.string.core_unit_km, major(meters, precision, locale))
        } else {
            strings.format(if (isImperial) R.string.core_unit_ft else R.string.core_unit_meters, minor(meters, locale))
        }
        return if (fromSteps) FROM_STEPS + text else text
    }

    /** [meters] in the small unit whatever its size, with none of [distance]'s floor: "0 m", "8 m", "528 ft". */
    fun minorText(meters: Float, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        strings.format(if (isImperial) R.string.core_unit_ft else R.string.core_unit_meters, minor(meters, locale))

    /** [meters] as a number and its unit drawn apart: "5.21" "km", "820" "m", "3.11" "mi", "528" "ft". Null under [SHOWN_FROM_METERS]. */
    fun figure(
        meters: Float, precision: Precision, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app,
    ): Figure? = when {
        meters < SHOWN_FROM_METERS -> null
        isMajor(meters) -> Figure(major(meters, precision, locale), majorUnit(strings))
        else -> Figure(minor(meters, locale), minorUnit(strings))
    }

    /** [meters] as a number of km or miles alone, for a figure with its unit drawn apart ([majorUnit]): "5.21". */
    fun major(meters: Float, precision: Precision, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.${precision.decimals}f", meters / majorMeters)

    /** Whole metres or feet, cut rather than rounded so 999.6 m never reads as a kilometre's 1000. */
    fun minor(meters: Float, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%d", (if (isImperial) meters * FEET_PER_METER else meters).toInt())

    /**
     * A distance goal's number in km or miles to at most [places] decimals, trailing zeros dropped, always with a dot
     * (it is typed back as written): "5", "7.5", "21.1".
     */
    fun goalNumber(meters: Int, places: Int): String =
        String.format(Locale.ROOT, "%.${places}f", meters / majorMeters.toDouble()).trimEnd('0').trimEnd('.')

    /**
     * Speed from [secondsPerKm], one decimal, in km/h or mph ([speedUnit]); null with none or slower than [Pace.SLOWEST_S].
     */
    fun speed(secondsPerKm: Double?, locale: Locale = Locale.getDefault()): String? =
        secondsPerKm?.takeIf { it > 0 && it < Pace.SLOWEST_S }?.let {
            String.format(locale, "%.1f", KMH_SECONDS / it / (splitMeters / METERS_PER_KM))
        }

    private const val KMH_SECONDS = 3600.0

    /** A count with the language's grouping: "23,410" steps, "23.410" in German. In dots ([DOT_LOCALE]), [dotGrouped]. */
    fun count(n: Long, locale: Locale = Locale.getDefault()): String =
        if (locale == DOT_LOCALE) dotGrouped(String.format(locale, "%d", n)) else String.format(locale, "%,d", n)

    /**
     * A number for the dot font (Halo System board, Dots): up to four digits run together ("8225"), five and up are
     * grouped in threes by a thin space ([DOT_GROUP_SPACE], "26 120"), never a comma or a dot, so a dot only ever means a
     * decimal. Any commas in [text] are dropped first; digits after a decimal dot are left alone.
     */
    fun dotGrouped(text: String): String = DOT_GROUPED_RUN.replace(text.replace(",", "")) { m ->
        m.value.reversed().chunked(DOT_GROUP).joinToString(DOT_GROUP_SPACE.toString()).reversed()
    }

    private val DOT_GROUPED_RUN = Regex("""(?<![.\d])\d{5,}""")
    private const val DOT_GROUP = 3

    /** The thin space between a big dot number's groups: narrower than a word space (DotFont draws it one column). */
    const val DOT_GROUP_SPACE = '\u2009'

    /** [count] for an Int. */
    fun count(n: Int, locale: Locale = Locale.getDefault()): String = count(n.toLong(), locale)

    /** [v] with [places] decimals in the phone's language, for text: "1.4", "1,4" in German. */
    fun decimal(v: Number, places: Int, locale: Locale = Locale.getDefault()): String = String.format(locale, "%.${places}f", v)

    /** A length of time: "40 min", "7 h", "7 h 40", "7 h 05". Under a minute is "0 min". */
    fun duration(seconds: Long, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val minutes = seconds.coerceAtLeast(0) / MINUTE
        val h = minutes / MINUTES_PER_HOUR
        val m = minutes % MINUTES_PER_HOUR
        // The numbers are written in [locale] here, so a caller's locale wins over the app's language for digits.
        return when {
            h == 0L -> strings.format(R.string.core_unit_minutes, String.format(locale, "%d", m))
            m == 0L -> strings.format(R.string.core_unit_hours, String.format(locale, "%d", h))
            else -> strings.format(R.string.core_unit_hours_minutes, String.format(locale, "%d", h), String.format(locale, "%02d", m))
        }
    }

    /**
     * [duration] for a figure drawn in the dot font ([locale] is [DOT_LOCALE] there): "7 h 40", "40 min". The big number
     * draws the digits as dots and the unit words small between them, so a length never takes a clock's look ("7:40").
     */
    fun clockDuration(seconds: Long, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        duration(seconds, locale, strings)

    /**
     * A finished workout's length, or a gap between two, written as every length is: "45 s" under a minute, "1 min 22"
     * under an hour, "1 h 02" from an hour. Seconds drop out from the hour, as in [duration].
     */
    fun length(seconds: Long, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String {
        val s = seconds.coerceAtLeast(0)
        return when {
            s < MINUTE -> strings.format(R.string.core_unit_seconds, String.format(locale, "%d", s))
            s < HOUR -> strings.format(
                R.string.core_unit_minutes_seconds, String.format(locale, "%d", s / MINUTE), String.format(locale, "%02d", s % MINUTE),
            )
            else -> duration(s, locale, strings)
        }
    }

    /** The workout timer: "12:34", or "1:02:03" past the hour. */
    fun workoutClock(seconds: Long, locale: Locale = Locale.getDefault()): String =
        if (seconds >= HOUR) String.format(locale, "%d:%02d:%02d", seconds / HOUR, seconds / MINUTE % MINUTES_PER_HOUR, seconds % MINUTE)
        else String.format(locale, "%d:%02d", seconds / MINUTE, seconds % MINUTE)

    /** A finished workout's length, or a gap between two: [length]. The running timer is [workoutClock]. */
    fun secondsOrClock(seconds: Long, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String =
        length(seconds, locale, strings)
}

/** A change against your usual, with its sign: "+3", "−3", or "±0" when it rounds to nothing, never "-0". */
object Signed {
    fun of(v: Int, locale: Locale = Locale.getDefault()): String {
        val text = String.format(locale, "%d", abs(v))
        return when {
            v > 0 -> "+$text"
            v < 0 -> "−$text"
            else -> "±$text"
        }
    }

    /** [v] to [decimals] places with its sign: "+0.4", "−3", "±0.0". */
    fun of(v: Float, decimals: Int, locale: Locale = Locale.getDefault()): String {
        val text = String.format(locale, "%.${decimals}f", abs(v))
        // Decided on the rounded value, not the text, so a comma decimal mark can't make "0,0" read as a change.
        val rounded = (abs(v) * TEN.pow(decimals)).roundToLong()
        return when {
            rounded == 0L -> "±$text"
            v > 0 -> "+$text"
            else -> "−$text"
        }
    }

    private const val TEN = 10f
}

/**
 * A pace, decision 5 in [Units]: "5:24/km" or "8:40/mi", from 100 m covered, nothing slower than an hour a km. Paces
 * are worked out and passed around in seconds a km, whatever the system, and turned into miles only here, as written.
 */
object Pace {
    /** Pace shows once a workout has covered this many metres: before that one GPS jump swings it wildly. */
    const val FROM_METERS = 100f

    /** A pace slower than this, seconds a km, is standing still and shows nothing. */
    const val SLOWEST_S = 3600.0

    /** Seconds a km for [meters] in [seconds], null with no distance or under [FROM_METERS]. */
    fun of(meters: Float?, seconds: Long): Double? =
        meters?.takeIf { it >= FROM_METERS }?.let { seconds / (it / Units.METERS_PER_KM.toDouble()) }

    /**
     * A pace as a clock alone, "5:24", for a figure with its unit drawn apart ([Units.paceUnit]), per mile in miles.
     * Null with none or slower than [SLOWEST_S] a km.
     */
    fun clock(secondsPerKm: Double?, locale: Locale = Locale.getDefault()): String? {
        if (secondsPerKm == null || secondsPerKm <= 0.0 || secondsPerKm > SLOWEST_S) return null
        val s = Math.round(secondsPerKm * Units.splitMeters / Units.METERS_PER_KM)
        return String.format(locale, "%d:%02d", s / Units.MINUTE, s % Units.MINUTE)
    }

    /** A pace with its unit, "5:24/km" or "8:40/mi", null as for [clock]. */
    fun text(secondsPerKm: Double?, locale: Locale = Locale.getDefault(), strings: Strings = Strings.app): String? =
        clock(secondsPerKm, locale)?.let { strings.format(Units.res(R.string.core_unit_pace, R.string.core_unit_pace_mi), it) }
}

/** A time of day, decision 4 in [Units]: every clock in the app follows the phone's 12 or 24 hour setting through here. */
object TimeOfDay {
    private const val MINUTES_PER_DAY = 1440

    /** [epoch] as a time of day: "22:30", or "10:30 PM" on a 12 hour phone. [use24Hour] is the phone's setting ([ClockSetting]). */
    fun format(epoch: Long, zone: ZoneId, use24Hour: Boolean = ClockSetting.use24Hour, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochSecond(epoch).atZone(zone).format(formatter(use24Hour, locale))

    /** A wall clock time with no date. */
    fun format(time: LocalTime, use24Hour: Boolean = ClockSetting.use24Hour, locale: Locale = Locale.getDefault()): String =
        time.format(formatter(use24Hour, locale))

    /** Minutes since midnight, wrapping past either end of the day: 1470 is "00:30". */
    fun minutes(minutes: Int, use24Hour: Boolean = ClockSetting.use24Hour, locale: Locale = Locale.getDefault()): String =
        format(LocalTime.ofSecondOfDay(Math.floorMod(minutes, MINUTES_PER_DAY) * Units.MINUTE), use24Hour, locale)

    private fun formatter(use24Hour: Boolean, locale: Locale) = Formatters.of(if (use24Hour) "HH:mm" else "h:mm a", locale)
}

/**
 * The phone's 12 or 24 hour setting, kept here so data code, widgets and tests can write a time without a Context.
 * The app sets it from `DateFormat.is24HourFormat` when it starts and when the setting changes. Until then it's 24
 * hours, as the app wrote every time before it followed the setting.
 */
object ClockSetting {
    @Volatile var use24Hour: Boolean = true
}

/**
 * [String.format] for text drawn in the dot font: ASCII digits and a dot decimal mark whatever the phone's language,
 * since the font has no other script's digits and a comma would read as a thousands mark. Big numbers are grouped
 * by a space ([Units.dotGrouped]).
 */
fun dotFormat(format: String, vararg args: Any?): String = Units.dotGrouped(String.format(Units.DOT_LOCALE, format, *args))

/**
 * Date and time formatters by pattern and locale, built once each. They write the locale's own digits
 * ([DecimalStyle.of]), so an Arabic phone's clocks and dates match its other numbers: a plain pattern would keep ASCII.
 */
internal object Formatters {
    private val cache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    fun of(pattern: String, locale: Locale): DateTimeFormatter =
        cache.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale).withDecimalStyle(DecimalStyle.of(locale)) }
}
