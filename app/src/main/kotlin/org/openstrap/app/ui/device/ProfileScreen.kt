package org.openstrap.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import org.openstrap.app.ui.dashboard.ScreenList
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openstrap.app.data.settings.Units
import org.openstrap.app.data.settings.UserProfile
import androidx.compose.foundation.layout.height
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageButton
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageNote
import org.openstrap.app.ui.glow.PagePanel
import org.openstrap.app.ui.glow.PageSwitchRow
import java.time.LocalDate
import org.openstrap.app.ui.theme.PhosphorIcons

/** One line for the settings row: "34 years, male, 72 kg, 178 cm", or a prompt when empty. */
fun UserProfile.summary(): String =
    if (isEmpty) "Add age and sex for accurate effort and recovery"
    else listOfNotNull(
        age()?.let { "$it years" },
        sex?.name?.lowercase(),
        weightKg?.let { Units.formatWeight(it, usesImperial) },
        heightCm?.let { Units.formatHeight(it, usesImperial) },
    ).joinToString(", ")

/**
 * Age, sex, weight and height. Age sets the maximum heart rate (and so every effort calculation);
 * sex picks the training-load curve. Kept on this phone only. A full page over the You list (or beside it, when
 * wide); back leaves without saving, asking first if anything was changed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(profile: UserProfile, contentPadding: PaddingValues, onSave: (UserProfile) -> Unit, onBack: () -> Unit) {
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
    var maxHr by rememberSaveable { mutableStateOf(profile.maxHeartRate?.toString().orEmpty()) }
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
    val maxHrValue = maxHr.trim().toIntOrNull()?.takeIf { it in 120..230 }
    // Hours, so "7.5" is 450 min; only 7-9 h is accepted (Recovery.SLEEP_NEED_MIN..MAX).
    val sleepNeedValue = sleepNeed.trim().replace(',', '.').toFloatOrNull()?.let { Math.round(it * 60) }
        ?.takeIf { it in org.openstrap.app.data.Recovery.SLEEP_NEED_MIN..org.openstrap.app.data.Recovery.SLEEP_NEED_MAX }
    val valid = (age.isBlank() || ageValue != null) && (weight.isBlank() || weightValue != null) && (heightBlank || heightValue != null) &&
        (sleepNeed.isBlank() || sleepNeedValue != null) && (maxHr.isBlank() || maxHrValue != null)

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
    val initial = rememberSaveable { listOf(age, sex, imperial, weight, heightCm, feet, inches, activity, betaBlocker, pregnant, maxHr, sleepNeed).joinToString("\u0000") }
    val dirty = listOf(age, sex, imperial, weight, heightCm, feet, inches, activity, betaBlocker, pregnant, maxHr, sleepNeed).joinToString("\u0000") != initial
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val back: () -> Unit = { if (dirty) confirmDiscard = true else onBack() }
    BackHandler(onBack = back)
    if (confirmDiscard) {
        org.openstrap.app.ui.components.GlassAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your profile changes haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
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
                maxHeartRate = maxHrValue,
                activityLevel = activity.takeIf { it in 0..4 },
                // Saving confirms the answers, so the fitness estimate's freshness checks restart.
                activityAnsweredAt = if (activity in 0..4) System.currentTimeMillis() / 1000 else null,
                bodyMeasuredAt = if (weightValue != null && heightValue != null) System.currentTimeMillis() / 1000 else null,
                betaBlocker = betaBlocker,
                pregnant = pregnant,
            ),
        )
    }

    // The keyboard shrinks the page rather than covering it, so a focused field scrolls into view above it;
    // while it's up the nav bar is hidden behind it and needs no room.
    Box(Modifier.fillMaxSize().imePadding()) {
        ScreenList(if (WindowInsets.isImeVisible) PaddingValues(0.dp) else contentPadding, gutter = GlowLayout.Gutter) {
            item {
                PageHeader("Your profile", back) {
                    PageButton("Save", save, enabled = valid, primary = true, modifier = Modifier.height(40.dp))
                }
            }
            item {
                PagePanel {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PageNote(
                            "Age sets your maximum heart rate, which every effort and recovery number depends on. " +
                                "All fields are optional and stay on this phone.",
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumberField(age, { age = it }, "Age", "years", age.isNotBlank() && ageValue == null, Modifier.weight(1f))
                            // Tapping the selected sex again clears it.
                            SexToggle(PhosphorIcons.GenderFemale, "Female", FemaleColor, sex == 0) { sex = if (sex == 0) 2 else 0 }
                            SexToggle(PhosphorIcons.GenderMale, "Male", MaleColor, sex == 1) { sex = if (sex == 1) 2 else 1 }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("Units")
                            org.openstrap.app.ui.glow.GlowSegments(listOf("Metric", "Imperial"), if (imperial) 1 else 0, { switchUnits(it == 1) })
                        }
                        NumberField(
                            weight, { weight = it }, "Weight", if (imperial) "lb" else "kg",
                            weight.isNotBlank() && weightValue == null, decimal = true,
                        )
                        if (imperial) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                NumberField(feet, { feet = it.take(1) }, "Feet", "ft", !heightBlank && heightValue == null, Modifier.weight(1f))
                                NumberField(inches, { inches = it.take(2) }, "Inches", "in", !heightBlank && heightValue == null, Modifier.weight(1f))
                            }
                        } else {
                            NumberField(heightCm, { heightCm = it }, "Height", "cm", !heightBlank && heightValue == null)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FieldLabel("Usual activity, for the fitness estimate")
                            // Tapping the chosen level again clears it.
                            org.openstrap.app.ui.glow.GlowSegments((1..5).map { "$it" }, activity, { i -> activity = if (activity == i) -1 else i })
                            PageNote(if (activity in 0..4) org.openstrap.app.data.Vo2max.ACTIVITY_LEVELS[activity] else "Pick the level that best matches a usual week.")
                        }
                        PageSwitchRow("Taking beta-blockers", null, betaBlocker, { betaBlocker = it }, divider = false)
                        PageSwitchRow("Pregnant", null, pregnant, { pregnant = it }, divider = false)
                        NumberField(maxHr, { maxHr = it }, "Max heart rate from a test (optional)", "bpm", maxHr.isNotBlank() && maxHrValue == null)
                        NumberField(sleepNeed, { sleepNeed = it }, "Sleep need (7–9, blank for your age's default)", "h", sleepNeed.isNotBlank() && sleepNeedValue == null, decimal = true)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    unit: String,
    error: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(5)) },
        label = if (label.isEmpty()) null else ({ Text(label) }),
        suffix = { Text(unit) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}

@Composable
private fun FieldLabel(text: String) = Text(text, color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary)

/** 72.0 -> "72", 158.7 -> "158.7". */
private fun formatNumber(v: Float): String = "%.1f".format(java.util.Locale.ROOT, v).removeSuffix(".0")

private val FemaleColor = Color(0xFFF472B6)
private val MaleColor = Color(0xFF60A5FA)

/** A round icon button for one sex, in its own colour: muted until selected, then filled. */
@Composable
private fun SexToggle(icon: ImageVector, label: String, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 8.dp).size(52.dp).clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .border(if (selected) 1.5.dp else 1.dp, if (selected) accent else Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClickLabel = label, role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected; contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (selected) accent else accent.copy(alpha = 0.55f), modifier = Modifier.size(26.dp))
    }
}
