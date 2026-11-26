package org.openstrap.app.ui.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openstrap.app.AppContainer
import org.openstrap.app.data.ForkReports
import org.openstrap.app.data.ForkReportsSource
import org.openstrap.app.data.catching
import org.openstrap.app.data.settings.UserProfile
import java.time.LocalDate

/**
 * The fork's weekly report for Today's card (jptorres-emp/openstrap): the Reports page's week, worked out for today
 * once the profile is read, and again whenever [after] changes (the morning's Recovery, as a sync brings it). Null until
 * then, or when it can't be worked out, which leaves the card out.
 */
@Composable
internal fun rememberForkWeek(container: AppContainer, after: Any?): ForkReports.Stretch? {
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null as UserProfile?)
    val source = remember(container) { ForkReportsSource(container.database.healthDao(), container.workouts) }
    val today = LocalDate.now()
    val report by produceState<ForkReports.Report?>(null, profile, today, after) {
        val shown = profile ?: return@produceState
        value = catching { source.load(shown) }.getOrNull()
    }
    return report?.takeIf { it.today == today }?.week
}
