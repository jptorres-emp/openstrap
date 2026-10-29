package org.openstrap.app.ui.device

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openstrap.app.BuildConfig
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.l10n.AppLanguage
import org.openstrap.app.l10n.L10n
import org.openstrap.app.l10n.tr
import org.openstrap.app.ui.dashboard.ScreenList
import org.openstrap.app.ui.glow.GlowLayout
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageHeader
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.glow.PageSheet
import org.openstrap.app.ui.theme.AppIcons

/** "7 h 30", or "8 h" on the hour. */
fun hoursMinutes(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

/** The Goals row's value: "8,000 steps, 8 active hours". */
fun goalsLine(goals: Goals): String =
    tr("%,d steps, %d active hours", "%,d passos, %d horas ativas").format(goals.steps, goals.activeHours)

/** The Sleep goal row's value, with the sleep need Recovery uses beside it: "7 h 30. Your sleep need is 8 h". */
fun sleepGoalLine(goals: Goals, needMinutes: Int): String =
    "${hoursMinutes(goals.sleepMinutes)}. " + tr("Your sleep need is ", "Precisas de dormir ") + hoursMinutes(needMinutes)

/** A language as the row and the picker name it, each in its own words. */
fun languageName(language: AppLanguage): String = when (language) {
    AppLanguage.System -> tr("Same as the phone", "Igual ao telemóvel")
    AppLanguage.English -> "English"
    AppLanguage.Portuguese -> "Português (Portugal)"
}

/**
 * The You page (spec 3.4), from the avatar on any tab: who you are and what happens to your data, things visited
 * monthly. A page like the Strap page rather than a sheet, because most rows open pages of their own and Back from
 * those should come back here. Every row shows its current value under its name, with an icon on its left. The
 * language row opens a picker; a new language draws the screen again in it, widgets too.
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
    onGoals: () -> Unit,
    onSaveCopy: () -> Unit,
    onRestoreCopy: () -> Unit,
    onHealthConnect: () -> Unit,
    onRestoreHealthConnect: () -> Unit,
    onDiagnostics: () -> Unit,
    onSampleData: (() -> Unit)?,
    onBack: () -> Unit,
    /** The row whose page is open beside the list on a wide screen, by its title ("Profile"), or null. */
    selected: String? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val language = remember { L10n.chosen(context) }
    var picking by rememberSaveable { mutableStateOf(false) }
    ScreenList(contentPadding, gutter = GlowLayout.Gutter) {
        item { PageHeader(tr("You", "Definições"), onBack) }
        item {
            PageRows {
                PageRow(tr("Profile", "Perfil"), profile.summary(), onClick = onProfile, below = true, divider = false, icon = AppIcons.User, selected = selected == "Profile")
                PageRow(tr("Goals", "Objetivos"), goalsLine(goals), onClick = onGoals, below = true, icon = AppIcons.Target)
                PageRow(tr("Sleep goal", "Objetivo de sono"), sleepGoalLine(goals, sleepNeedMinutes), onClick = onGoals, below = true, icon = AppIcons.Moon)
                PageRow(tr("Language", "Idioma"), languageName(language), onClick = { picking = true }, below = true, icon = AppIcons.Globe)
            }
        }
        item { Text(tr("Your data", "Os teus dados"), color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary, modifier = Modifier.padding(start = 18.dp, top = 4.dp)) }
        item {
            PageRows {
                PageRow(
                    tr("Save a copy", "Guardar uma cópia"), tr("A zip of every synced record, saved where you choose", "Um zip com tudo o que foi sincronizado, onde escolheres"),
                    onClick = onSaveCopy, below = true, divider = false, icon = AppIcons.DownloadSimple,
                )
                PageRow(tr("Restore from a copy", "Repor a partir de uma cópia"), tr("For days before the strap's history", "Para dias anteriores ao histórico da strap"), onClick = onRestoreCopy, below = true, icon = AppIcons.UploadSimple)
                PageRow("Health Connect", tr("Copy your data to Android Health Connect", "Copiar os teus dados para o Health Connect do Android"), onClick = onHealthConnect, below = true, icon = AppIcons.Heartbeat)
                PageRow(tr("Restore from Health Connect", "Repor do Health Connect"), tr("Bring back what OpenStrap saved there", "Recuperar o que o OpenStrap lá guardou"), onClick = onRestoreHealthConnect, below = true, icon = AppIcons.ArrowCounterClockwise)
            }
        }
        item {
            PageRows {
                PageRow(tr("Diagnostics", "Diagnóstico"), tr("$logLines lines, for bug reports", "$logLines linhas, para relatar erros"), onClick = onDiagnostics, below = true, divider = false, icon = AppIcons.Bug, selected = selected == "Diagnostics")
                if (onSampleData != null) PageRow(tr("Load sample data", "Carregar dados de exemplo"), tr("Debug builds only", "Só em versões de teste"), onClick = onSampleData, below = true, icon = AppIcons.Flask)
            }
        }
        item {
            Text(
                "OpenStrap ${BuildConfig.VERSION_NAME}. " + tr("Everything stays on this phone.", "Tudo fica neste telemóvel."),
                Modifier.fillMaxWidth().padding(top = 4.dp),
                color = Color.White.copy(alpha = 0.45f), style = GlowType.secondary, textAlign = TextAlign.Center,
            )
        }
    }
    if (picking) LanguageSheet(
        language,
        onPick = { choice ->
            picking = false
            if (choice != language) {
                L10n.choose(context, choice)
                // Widgets and the screen speak the new language straight away.
                org.openstrap.app.device.WidgetUpdater.changedAll(context.applicationContext)
                context.activity()?.recreate()
            }
        },
        onDismiss = { picking = false },
    )
}

/** The language picker: the phone's own, English or Portuguese, the one in use ticked. */
@Composable
private fun LanguageSheet(current: AppLanguage, onPick: (AppLanguage) -> Unit, onDismiss: () -> Unit) {
    PageSheet(onDismiss, tr("Language", "Idioma"), tr("Dates and numbers follow it too.", "As datas e os números também o seguem.")) {
        PageRows {
            AppLanguage.entries.forEachIndexed { i, choice ->
                PageRow(
                    languageName(choice), onClick = { onPick(choice) }, divider = i > 0, icon = AppIcons.Globe,
                    trailing = {
                        if (choice == current) Icon(AppIcons.CheckCircle, tr("In use", "Em uso"), Modifier.size(20.dp), tint = Color.White)
                    },
                )
            }
        }
    }
}

/** The activity behind a composable's [Context], through any wrappers. */
private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
