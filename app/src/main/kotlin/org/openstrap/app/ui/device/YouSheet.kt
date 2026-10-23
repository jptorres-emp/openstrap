package org.openstrap.app.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openstrap.app.BuildConfig
import org.openstrap.app.data.settings.Goals
import org.openstrap.app.data.settings.UserProfile
import org.openstrap.app.ui.glow.GlowType
import org.openstrap.app.ui.glow.PageRow
import org.openstrap.app.ui.glow.PageRows
import org.openstrap.app.ui.theme.PhosphorIcons

/** "7 h 30", or "8 h" on the hour. */
fun hoursMinutes(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"

/** The Goals row's value: "8,000 steps, 8 active hours". */
fun goalsLine(goals: Goals): String = "%,d steps, %d active hours".format(goals.steps, goals.activeHours)

/** The Sleep goal row's value, with the sleep need Recovery uses beside it: "7 h 30. Your sleep need is 8 h". */
fun sleepGoalLine(goals: Goals, needMinutes: Int): String =
    "${hoursMinutes(goals.sleepMinutes)}. Your sleep need is ${hoursMinutes(needMinutes)}"

/**
 * The You sheet (spec 3.4), from the avatar on any tab: who you are and what happens to your data, things visited
 * monthly, so a sheet over the tab rather than a tab of their own. Every row shows its current value under its name.
 */
@Composable
fun YouSheet(
    profile: UserProfile,
    goals: Goals,
    /** The sleep need Recovery uses, in minutes: the profile's own or the one for your age. */
    sleepNeedMinutes: Int,
    logLines: Int,
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onGoals: () -> Unit,
    onSaveCopy: () -> Unit,
    onRestoreCopy: () -> Unit,
    onHealthConnect: () -> Unit,
    onRestoreHealthConnect: () -> Unit,
    onDiagnostics: () -> Unit,
    onSampleData: (() -> Unit)?,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    org.openstrap.app.ui.components.GlassSheet(onDismiss) {
        Column(
            Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).background(Color(0xFF1C1C1E), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.User, null, Modifier.size(26.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                // The profile's summary is on its row below, not repeated here.
                Text("You", color = Color.White, style = GlowType.label.copy(fontSize = 20.sp, lineHeight = 26.sp))
            }
            PageRows {
                PageRow("Profile", profile.summary(), onClick = onProfile, below = true, divider = false)
                PageRow("Goals", goalsLine(goals), onClick = onGoals, below = true)
                PageRow("Sleep goal", sleepGoalLine(goals, sleepNeedMinutes), onClick = onGoals, below = true)
            }
            Text("Your data", color = Color.White.copy(alpha = 0.6f), style = GlowType.secondary, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
            PageRows {
                PageRow("Save a copy", "A zip of every synced record, saved where you choose", onClick = onSaveCopy, below = true, divider = false)
                PageRow("Restore from a copy", "For days before the strap's history", onClick = onRestoreCopy, below = true)
                PageRow("Health Connect", "Copy your data to Android Health Connect", onClick = onHealthConnect, below = true)
                PageRow("Restore from Health Connect", "Bring back what OpenStrap saved there", onClick = onRestoreHealthConnect, below = true)
            }
            PageRows {
                PageRow("Diagnostics", "$logLines lines, for bug reports", onClick = onDiagnostics, below = true, divider = false)
                if (onSampleData != null) PageRow("Load sample data", "Debug builds only", onClick = onSampleData, below = true)
            }
            Text(
                "OpenStrap ${BuildConfig.VERSION_NAME}. Everything stays on this phone.",
                Modifier.fillMaxWidth().padding(top = 4.dp),
                color = Color.White.copy(alpha = 0.45f), style = GlowType.secondary, textAlign = TextAlign.Center,
            )
        }
    }
}
