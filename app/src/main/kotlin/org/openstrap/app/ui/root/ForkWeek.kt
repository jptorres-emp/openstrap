package org.openstrap.app.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openstrap.app.AppContainer
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkReportsSource
import org.openstrap.app.data.Today
import org.openstrap.app.data.catching
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate

/**
 * The fork's weekly report for Today's card (jptorres-emp/openstrap): the Reports page's week, worked out for today
 * once the profile is read, again when the date moves ([Today.dates]) and after each sync that ends well ([synced],
 * when the last did). Null until then, or when it can't be worked out, which leaves the card out.
 */
@Composable
internal fun rememberForkWeek(container: AppContainer, synced: Long?): ForkReports.Stretch? {
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null as UserProfile?)
    val today by remember { Today.dates() }.collectAsStateWithLifecycle(initialValue = null as LocalDate?)
    val source = remember(container) { ForkReportsSource(container.database.healthDao(), container.workouts) }
    val report by produceState<ForkReports.Report?>(null, profile, today, synced) {
        val shown = profile ?: return@produceState
        val day = today ?: return@produceState
        value = catching { source.load(shown, clock = { day }) }.getOrNull()
    }
    return report?.takeIf { it.today == today }?.week
}
