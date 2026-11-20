package org.openstrap.app.ui.device

import org.openstrap.app.ui.glow.HaloPageHeader
import org.openstrap.app.ui.glow.HaloPageList
import org.openstrap.app.ui.glow.PageVariant
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.openstrap.app.BuildConfig
import org.openstrap.app.R
import org.openstrap.app.data.settings.BackupState
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.HealthConnectAuto
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.health.HcAvailability
import org.openstrap.app.i18n.Strings
import org.openstrap.app.presentation.DayWords
import org.openstrap.app.presentation.Stamp
import org.openstrap.app.presentation.Units
import java.util.concurrent.TimeUnit.MINUTES
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.update.UpdateCopy
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.NavRow
import org.openstrap.app.ui.theme.AppIcons

/** The Goals row's value: "8,000 steps, 300 active kcal, 8 active hours". */
fun goalsLine(goals: Goals, locale: java.util.Locale = java.util.Locale.getDefault(), strings: Strings = Strings.app): String =
    strings.format(
        R.string.you_goals_line,
        strings.plural(R.plurals.you_goals_steps, goals.steps, Units.count(goals.steps.toLong(), locale)),
        strings.format(R.string.you_goals_active_kcal, Units.count(goals.calories.toLong(), locale)),
        strings.plural(R.plurals.you_goals_active_hours, goals.activeHours, goals.activeHours),
    )

/** The Sleep goal row's value, with the sleep need Recovery uses beside it: "7 h 30, sleep need 8 h". */
fun sleepGoalLine(goals: Goals, needMinutes: Int, strings: Strings = Strings.app): String =
    strings.format(
        R.string.you_sleep_goal_line,
        Units.duration(MINUTES.toSeconds(goals.sleepMinutes.toLong())),
        Units.duration(MINUTES.toSeconds(needMinutes.toLong())),
    )

/**
 * The auto sync row's value: on and when it last wrote, by [Stamp.of] ("On, last copied 10:42", "Yesterday 10:42",
 * "Tue 10 Nov, 10:42"), off, or stopped by itself and why. [now] in epoch seconds. Stopped with
 * Health Connect gone says to install it where the Play Store has it ([availability]).
 */
fun healthConnectLine(
    auto: HealthConnectAuto,
    now: Long,
    zone: java.time.ZoneId,
    locale: java.util.Locale = java.util.Locale.getDefault(),
    availability: HcAvailability = HcAvailability.AVAILABLE,
    strings: Strings = Strings.app,
): String {
    if (!auto.on) return strings.get(
        when (auto.stopped) {
            HealthConnectAuto.Stopped.PERMISSIONS -> R.string.you_hc_line_stopped_permissions
            HealthConnectAuto.Stopped.UNAVAILABLE ->
                if (availability == HcAvailability.INSTALLABLE) R.string.you_hc_line_stopped_install
                else R.string.you_hc_line_stopped_unavailable
            null -> R.string.you_hc_line_off
        },
    )
    val last = auto.lastSynced ?: return strings.get(R.string.you_hc_line_on)
    return strings.format(R.string.you_hc_line_last, Stamp.of(last, now, zone, locale = locale))
}

/** The You page's Health Connect row: on and when it last wrote, off, or stopped and why, in a few words. */
fun healthConnectSummary(
    auto: HealthConnectAuto,
    now: Long,
    zone: java.time.ZoneId,
    locale: java.util.Locale = java.util.Locale.getDefault(),
    availability: HcAvailability = HcAvailability.AVAILABLE,
    strings: Strings = Strings.app,
): String =
    when {
        auto.on && auto.lastSynced != null -> healthConnectLine(auto, now, zone, locale, strings = strings)
        auto.on -> strings.get(R.string.you_value_on)
        auto.stopped == HealthConnectAuto.Stopped.PERMISSIONS -> strings.get(R.string.you_hc_summary_stopped_permissions)
        auto.stopped == HealthConnectAuto.Stopped.UNAVAILABLE -> strings.get(
            if (availability == HcAvailability.INSTALLABLE) R.string.you_hc_summary_stopped_install
            else R.string.you_hc_summary_stopped_unavailable,
        )
        else -> strings.get(R.string.you_value_off)
    }

/**
 * The You page (spec 3.4), from the avatar on any tab: who you are and what happens to your data, things visited
 * monthly. A page like the Strap page rather than a sheet, because most rows open pages of their own and Back from
 * those should come back here. Every row shows its current value under its name, with an icon on its left.
 */
@Composable
fun YouPage(
    profile: UserProfile,
    goals: Goals,
    /** The sleep need Recovery uses, in minutes: the profile's own or the one for your age. */
    sleepNeedMinutes: Int,
    logLines: Int,
    contentPadding: PaddingValues,
    onProfile: () -> Unit,
    /** Your max heart rate once worked out, said on the Heart rate zones row, which opens that page. */
    maxHeartRate: Int?,
    onHeartZones: () -> Unit,
    onGoals: () -> Unit,
    onSleepGoal: () -> Unit,
    /** The weekly goal, personal bests and badges (workouts spec 3.5). */
    onGoalsAndBests: () -> Unit,
    /** "Ask to end when I stop", said on the Workouts row, which opens the Workouts settings page. */
    askToEnd: org.openstrap.app.data.settings.AskToEnd,
    onWorkouts: () -> Unit,
    /** Auto sync to Health Connect, said on its row; the row opens the Health Connect page. */
    healthConnectAuto: HealthConnectAuto,
    /** Whether Health Connect is there or can be installed, for the row's stopped wording. */
    healthConnectAvailability: HcAvailability,
    onHealthConnect: () -> Unit,
    /** Automatic backup, said on its row; the row opens the Backup page. */
    backup: BackupState,
    onBackup: () -> Unit,
    /** Sleep and naps deleted in the last 7 days; the Deleted sleep row shows only when there are any. */
    deletedSleep: Int,
    onDeletedSleep: () -> Unit,
    /** New versions from GitHub, said on its row; the row opens the Updates page. */
    updates: org.openstrap.app.update.UpdateState,
    installedVersion: String,
    /** A new version the Updates page hasn't shown yet: a dot after the row's name. */
    updateWaiting: Boolean,
    onUpdates: () -> Unit,
    onDiagnostics: () -> Unit,
    onSampleData: (() -> Unit)?,
    onBack: () -> Unit,
    /** The row whose page is open beside the list on a wide screen, by its key in [PageRowKeys], or null. */
    selected: String? = null,
) {
    BackHandler(onBack = onBack)
    HaloPageList(contentPadding, variant = PageVariant.Settings) {
        item { HaloPageHeader(stringResource(R.string.you_title), onBack) }
        item {
            PageGroup(heading = stringResource(R.string.you_section_about_you)) {
                NavRow(
                    stringResource(R.string.you_row_profile), profile.summary(), onClick = onProfile, below = true, divider = false,
                    icon = AppIcons.User,
                    selected = selected == PageRowKeys.PROFILE,
                )
                NavRow(
                    org.openstrap.app.ui.workout.HeartZonesCopy.TITLE, org.openstrap.app.ui.workout.HeartZonesCopy.youLine(maxHeartRate), onClick = onHeartZones,
                    below = true, icon = AppIcons.Heartbeat, selected = selected == PageRowKeys.HEART_ZONES,
                )
                NavRow(stringResource(R.string.you_row_goals), goalsLine(goals), onClick = onGoals, below = true, icon = AppIcons.Target)
                NavRow(
                    stringResource(R.string.you_row_sleep_goal), sleepGoalLine(goals, sleepNeedMinutes), onClick = onSleepGoal,
                    below = true, icon = AppIcons.Moon,
                )
                NavRow(
                    stringResource(R.string.you_row_goals_and_bests),
                    pluralStringResource(R.plurals.you_goals_and_bests_line, goals.workoutsPerWeek, goals.workoutsPerWeek),
                    onClick = onGoalsAndBests,
                    below = true, icon = AppIcons.FlagCheckered, selected = selected == PageRowKeys.GOALS_AND_BESTS,
                )
                NavRow(
                    stringResource(R.string.ask_end_page_title), AskToEndCopy.youLine(askToEnd), onClick = onWorkouts,
                    below = true, icon = AppIcons.PersonSimpleRun, selected = selected == PageRowKeys.WORKOUTS,
                )
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.you_section_your_data), footnote = stringResource(R.string.you_data_footnote)) {
                // Read again whenever either changes, so "today" and "yesterday" follow the last one.
                val now = remember(healthConnectAuto, backup) { System.currentTimeMillis() / 1000 }
                val zone = java.time.ZoneId.systemDefault()
                NavRow(
                    stringResource(R.string.you_row_health_connect),
                    healthConnectSummary(healthConnectAuto, now, zone, availability = healthConnectAvailability), onClick = onHealthConnect,
                    below = true, divider = false,
                    icon = AppIcons.Heartbeat, selected = selected == PageRowKeys.HEALTH_CONNECT,
                )
                NavRow(
                    stringResource(R.string.you_row_backup), BackupCopy.summary(backup, now, zone), onClick = onBackup, below = true,
                    icon = AppIcons.ShieldCheck, selected = selected == PageRowKeys.BACKUP,
                )
                if (deletedSleep > 0) NavRow(
                    stringResource(R.string.you_row_deleted_sleep), org.openstrap.app.ui.dashboard.SleepCopy.deletedCount(deletedSleep),
                    onClick = onDeletedSleep, below = true,
                    icon = AppIcons.Trash, selected = selected == PageRowKeys.DELETED_SLEEP,
                )
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.you_section_help)) {
                val now = remember(updates) { System.currentTimeMillis() / 1000 }
                NavRow(
                    stringResource(R.string.you_row_updates), UpdateCopy.summary(updates, installedVersion, now), onClick = onUpdates,
                    below = true, divider = false,
                    icon = AppIcons.ArrowCircleUp, selected = selected == PageRowKeys.UPDATES, dot = updateWaiting,
                )
                NavRow(
                    stringResource(R.string.you_row_diagnostics), pluralStringResource(R.plurals.you_diagnostics_line, logLines, logLines),
                    onClick = onDiagnostics, below = true, icon = AppIcons.Bug, selected = selected == PageRowKeys.DIAGNOSTICS,
                )
                if (onSampleData != null) NavRow(
                    stringResource(R.string.you_row_sample_data), stringResource(R.string.you_row_sample_data_note), onClick = onSampleData,
                    below = true,
                    icon = AppIcons.Flask,
                )
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.you_section_about)) {
                NavRow(stringResource(R.string.you_row_version), stringResource(R.string.you_version_value, BuildConfig.VERSION_NAME),
                    divider = false)
            }
        }
    }
}
