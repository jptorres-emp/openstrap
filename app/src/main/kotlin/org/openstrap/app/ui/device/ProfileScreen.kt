@file:Suppress("MagicNumber") // the ranges and steps of the pickers are the page's own limits

package org.openstrap.app.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.settings.Units
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.i18n.Strings
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.PageConfirm
import org.openstrap.app.ui.glow.PageGroup
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageSaveButton
import org.openstrap.app.ui.glow.PageSwitchRow
import java.time.LocalDate

/** One line for the settings row: "34 years, male, 72 kg, 178 cm", or a prompt when empty. */
fun UserProfile.summary(strings: Strings = Strings.app): String =
    if (isEmpty) strings.get(R.string.you_profile_empty)
    else listOfNotNull(
        age()?.let { strings.plural(R.plurals.you_profile_age, it, it) },
        sex?.let {
            strings.get(
                when (it) {
                    UserProfile.Sex.Female -> R.string.you_profile_summary_female
                    UserProfile.Sex.Male -> R.string.you_profile_summary_male
                },
            )
        },
        weightKg?.let { Units.formatWeight(it, usesImperial) },
        heightCm?.let { Units.formatHeight(it, usesImperial) },
    ).joinToString(", ")

/**
 * Age, sex, weight and height. Age sets the maximum heart rate (and so every effort calculation);
 * sex picks the training-load curve. Kept on this phone only. A full page over the You list (or beside it, when
 * wide); back leaves without saving, asking first if anything was changed.
 */
@Composable
fun ProfileScreen(profile: UserProfile, contentPadding: PaddingValues, onSave: (UserProfile) -> Unit, onBack: () -> Unit, onHeartZones: () -> Unit = {}) {
    var age by rememberSaveable { mutableStateOf(profile.age()?.toString().orEmpty()) }
    var sex by rememberSaveable { mutableIntStateOf(when (profile.sex) { UserProfile.Sex.Female -> 0; UserProfile.Sex.Male -> 1; null -> 2 }) }
    var imperial by rememberSaveable { mutableStateOf(profile.usesImperial) }
    // Entry text in the chosen units; converting on a unit switch keeps what the user typed.
    var weight by rememberSaveable { mutableStateOf(profile.weightKg?.let { formatNumber(if (imperial) Units.kgToLb(it) else it) }.orEmpty()) }
    var heightCm by rememberSaveable { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    val (ft0, in0) = profile.heightCm?.let { Units.cmToFeetInches(it) } ?: (null to null)
    var feet by rememberSaveable { mutableStateOf(ft0?.toString().orEmpty()) }
    var inches by rememberSaveable { mutableStateOf(in0?.toString().orEmpty()) }
    var activity by rememberSaveable { mutableIntStateOf(profile.activityLevel ?: -1) }
    var betaBlocker by rememberSaveable { mutableStateOf(profile.betaBlocker) }
    var pregnant by rememberSaveable { mutableStateOf(profile.pregnant) }
    var sleepNeed by rememberSaveable { mutableStateOf(profile.sleepNeedMinutes?.let { formatNumber(it / 60f) }.orEmpty()) }

    val ageValue = age.trim().toIntOrNull()?.takeIf { it in 10..100 }
    val weightNumber = weight.trim().replace(',', '.').toFloatOrNull()
    val weightValue = weightNumber?.let { if (imperial) Units.lbToKg(it) else it }?.takeIf { it in 25f..300f }
    val heightValue = if (imperial) {
        val f = feet.trim().toIntOrNull()
        val i = inches.trim().ifEmpty { "0" }.toIntOrNull()?.takeIf { it in 0..11 }
        if (f != null && i != null) Units.feetInchesToCm(f, i).takeIf { it in 100..250 } else null
    } else heightCm.trim().toIntOrNull()?.takeIf { it in 100..250 }
    val heightBlank = if (imperial) feet.isBlank() && inches.isBlank() else heightCm.isBlank()
    // Hours, so "7.5" is 450 min; only 7-9 h is accepted (Recovery.SLEEP_NEED_MIN..MAX).
    val sleepNeedValue = sleepNeed.trim().replace(',', '.').toFloatOrNull()?.let { Math.round(it * 60) }
        ?.takeIf { it in org.openstrap.app.data.Recovery.SLEEP_NEED_MIN..org.openstrap.app.data.Recovery.SLEEP_NEED_MAX }
    val valid = (age.isBlank() || ageValue != null) && (weight.isBlank() || weightValue != null) && (heightBlank || heightValue != null) &&
        (sleepNeed.isBlank() || sleepNeedValue != null)

    fun switchUnits(toImperial: Boolean) {
        if (toImperial == imperial) return
        // Carry valid entries across so switching never loses what was typed.
        weightValue?.let { weight = formatNumber(if (toImperial) Units.kgToLb(it) else it) }
        heightValue?.let { cm ->
            heightCm = cm.toString()
            Units.cmToFeetInches(cm).let { (f, i) -> feet = f.toString(); inches = i.toString() }
        }
        imperial = toImperial
    }

    // What the fields held when the page opened, so back knows whether there is anything to lose.
    val initial = rememberSaveable { listOf(age, sex, imperial, weight, heightCm, feet, inches, activity, betaBlocker, pregnant, sleepNeed).joinToString("\u0000") }
    val dirty = listOf(age, sex, imperial, weight, heightCm, feet, inches, activity, betaBlocker, pregnant, sleepNeed).joinToString("\u0000") != initial
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)
    if (confirmDiscard) {
        PageConfirm(
            stringResource(R.string.you_profile_discard_title), stringResource(R.string.you_profile_discard_text),
            confirm = stringResource(R.string.you_profile_discard), onConfirm = { confirmDiscard = false; onBack() },
            onDismiss = { confirmDiscard = false },
            dismiss = stringResource(R.string.you_profile_keep_editing),
        )
    }
    val save: () -> Unit = {
        onSave(
            // A copy, so fields this page doesn't edit are kept.
            profile.copy(
                birthYear = ageValue?.let { LocalDate.now().year - it },
                sex = when (sex) { 0 -> UserProfile.Sex.Female; 1 -> UserProfile.Sex.Male; else -> null },
                weightKg = weightValue,
                heightCm = heightValue,
                imperial = imperial,
                sleepNeedMinutes = sleepNeedValue,
                activityLevel = activity.takeIf { it in 0..4 },
                // Saving confirms the answers, so the fitness estimate's freshness checks restart.
                activityAnsweredAt = if (activity in 0..4) System.currentTimeMillis() / 1000 else null,
                bodyMeasuredAt = if (weightValue != null && heightValue != null) System.currentTimeMillis() / 1000 else null,
                betaBlocker = betaBlocker,
                pregnant = pregnant,
            ),
        )
    }

    // Each value opens a picker sheet with Cancel and Save (HaloProfile and HaloProfileAgeSheet boards). A sheet's Save
    // only changes the page's draft; the header's Save pill, shown while there are edits, is what keeps them.
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val notSet = stringResource(R.string.you_profile_not_set)
    val unitKg = stringResource(R.string.you_profile_unit_kg)
    val unitLb = stringResource(R.string.you_profile_unit_lb)
    val unitFt = stringResource(R.string.you_profile_unit_ft)
    val unitIn = stringResource(R.string.you_profile_unit_in)
    val levels = remember { org.openstrap.app.data.Vo2max.ACTIVITY_LEVELS }

    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item {
            PageHeader(
                stringResource(R.string.you_profile_title), back,
                trailing = if (dirty) ({ PageSaveButton(stringResource(R.string.you_action_save), save, enabled = valid) }) else null,
            )
        }
        item {
            PageGroup(
                heading = stringResource(R.string.you_profile_group_about),
                footnote = stringResource(R.string.you_profile_foot_about),
            ) {
                PageRow(
                    stringResource(R.string.you_profile_age),
                    ageValue?.let { pluralStringResource(R.plurals.you_profile_age, it, it) } ?: notSet,
                    onClick = { sheet = "age" }, divider = false,
                )
                PageRow(
                    stringResource(R.string.you_profile_row_sex),
                    when (sex) {
                        0 -> stringResource(R.string.you_profile_sex_female)
                        1 -> stringResource(R.string.you_profile_sex_male)
                        else -> notSet
                    },
                    onClick = { sheet = "sex" },
                )
            }
        }
        item {
            PageGroup(heading = stringResource(R.string.you_profile_group_body)) {
                PageRow(
                    stringResource(R.string.you_profile_units),
                    stringResource(if (imperial) R.string.you_profile_units_imperial else R.string.you_profile_units_metric),
                    onClick = { sheet = "units" }, divider = false,
                )
                PageRow(
                    stringResource(R.string.you_profile_weight), weightValue?.let { Units.formatWeight(it, imperial) } ?: notSet,
                    onClick = { sheet = "weight" },
                )
                PageRow(
                    stringResource(R.string.you_profile_height), heightValue?.let { Units.formatHeight(it, imperial) } ?: notSet,
                    onClick = { sheet = "height" },
                )
            }
        }
        item {
            PageGroup(
                heading = stringResource(R.string.you_profile_group_estimates),
                footnote = stringResource(R.string.you_profile_foot_all),
            ) {
                PageRow(
                    stringResource(R.string.you_profile_row_activity), levels.getOrNull(activity) ?: notSet,
                    onClick = { sheet = "activity" }, divider = false,
                )
                PageRow(
                    stringResource(R.string.you_profile_row_sleep_need),
                    sleepNeedValue?.let { org.openstrap.app.presentation.Units.duration(it * 60L) }
                        ?: stringResource(R.string.you_profile_sleep_default),
                    onClick = { sheet = "sleep" },
                )
                PageSwitchRow(
                    stringResource(R.string.you_profile_beta_blockers), stringResource(R.string.you_profile_beta_note),
                    betaBlocker, { betaBlocker = it },
                )
                PageSwitchRow(stringResource(R.string.you_profile_pregnant), null, pregnant, { pregnant = it })
                // Your max is set on the Heart rate zones page, with the zones that follow it.
                PageRow(
                    org.openstrap.app.ui.workout.HeartZonesCopy.MAX_ROW,
                    org.openstrap.app.ui.workout.HeartZonesCopy.profileLine(profile.maxHeartRate),
                    onClick = onHeartZones, below = true,
                )
            }
        }
    }

    when (sheet) {
        "age" -> StepperSheet(
            stringResource(R.string.you_profile_age), ageValue ?: DEFAULT_AGE, AGE_RANGE,
            onSave = { age = it.toString(); sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (age.isNotBlank()) stringResource(R.string.you_profile_clear) else null, onClear = { age = ""; sheet = null },
            note = stringResource(R.string.you_profile_age_note), unit = stringResource(R.string.you_profile_age_unit),
        )
        "sex" -> SegmentSheet(
            stringResource(R.string.you_profile_row_sex),
            listOf(stringResource(R.string.you_profile_sex_female), stringResource(R.string.you_profile_sex_male)),
            if (sex in 0..1) sex else -1,
            onSave = { sex = it; sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (sex in 0..1) stringResource(R.string.you_profile_clear) else null,
            onClear = { sex = 2; sheet = null }, note = stringResource(R.string.you_profile_sex_sheet_note),
        )
        "units" -> SegmentSheet(
            stringResource(R.string.you_profile_units),
            listOf(stringResource(R.string.you_profile_units_metric), stringResource(R.string.you_profile_units_imperial)),
            if (imperial) 1 else 0,
            onSave = { switchUnits(it == 1); sheet = null }, onDismiss = { sheet = null },
        )
        "weight" -> StepperSheet(
            stringResource(R.string.you_profile_weight),
            weightNumber?.let { Math.round(it) } ?: if (imperial) DEFAULT_WEIGHT_LB else DEFAULT_WEIGHT_KG,
            if (imperial) 55..660 else 25..300,
            onSave = { weight = it.toString(); sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (weight.isNotBlank()) stringResource(R.string.you_profile_clear) else null,
            onClear = { weight = ""; sheet = null }, unit = if (imperial) unitLb else unitKg,
        )
        "height" -> if (imperial) StepperSheet(
            stringResource(R.string.you_profile_height),
            heightValue?.let { Units.cmToFeetInches(it).let { (f, i) -> f * 12 + i } } ?: DEFAULT_HEIGHT_IN, 40..98,
            onSave = { total ->
                feet = (total / 12).toString(); inches = (total % 12).toString()
                heightCm = Units.feetInchesToCm(total / 12, total % 12).toString(); sheet = null
            },
            onDismiss = { sheet = null }, format = { "${it / 12} $unitFt ${it % 12} $unitIn" },
            clearLabel = if (!heightBlank) stringResource(R.string.you_profile_clear) else null,
            onClear = { heightCm = ""; feet = ""; inches = ""; sheet = null },
        ) else StepperSheet(
            stringResource(R.string.you_profile_height), heightValue ?: DEFAULT_HEIGHT_CM, 100..250,
            onSave = { heightCm = it.toString(); sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (!heightBlank) stringResource(R.string.you_profile_clear) else null,
            onClear = { heightCm = ""; feet = ""; inches = ""; sheet = null },
            unit = stringResource(R.string.you_profile_unit_cm),
        )
        "activity" -> RadioSheet(
            stringResource(R.string.you_profile_row_activity), levels, activity,
            onSave = { activity = it; sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (activity in 0..4) stringResource(R.string.you_profile_clear) else null,
            onClear = { activity = -1; sheet = null },
            note = stringResource(R.string.you_profile_activity_note),
        )
        "sleep" -> StepperSheet(
            stringResource(R.string.you_profile_row_sleep_need),
            sleepNeedValue?.let { Math.round(it / 30f) } ?: DEFAULT_SLEEP_HALF_HOURS, 14..18,
            onSave = { sleepNeed = formatNumber(it / 2f); sheet = null }, onDismiss = { sheet = null },
            clearLabel = if (sleepNeed.isNotBlank()) stringResource(R.string.you_profile_sleep_use_usual) else null,
            onClear = { sleepNeed = ""; sheet = null },
            note = stringResource(R.string.you_profile_sleep_note), unit = stringResource(R.string.you_unit_h),
            format = { formatNumber(it / 2f) },
        )
    }
}

// Where a stepper starts when the field is empty.
private const val DEFAULT_AGE = 30
private val AGE_RANGE = 10..100
private const val DEFAULT_WEIGHT_KG = 70
private const val DEFAULT_WEIGHT_LB = 155
private const val DEFAULT_HEIGHT_CM = 170
private const val DEFAULT_HEIGHT_IN = 67
private const val DEFAULT_SLEEP_HALF_HOURS = 16

/** 72.0 -> "72", 158.7 -> "158.7". */
private fun formatNumber(v: Float): String = "%.1f".format(java.util.Locale.ROOT, v).removeSuffix(".0")
