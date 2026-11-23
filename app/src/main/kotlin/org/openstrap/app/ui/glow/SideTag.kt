package org.openstrap.app.ui.glow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.openstrap.app.R
import org.openstrap.app.data.Workouts
import org.openstrap.app.presentation.TabCopy

/**
 * The small grey Indoor tag beside a sport's name (board HaloRunNaming): a run or a ride has one name on either side,
 * and a workout of [type] done on the indoor side ([Workouts.isIndoorSide]) says so here. Nothing for the outdoor side,
 * whose map shows it, or a sport with one kind.
 */
@Composable
internal fun SideTag(type: Int, modifier: Modifier = Modifier) {
    if (Workouts.isIndoorSide(type)) HaloTag(stringResource(R.string.workout_side_tag_indoor), modifier, TagVariant.Quiet)
}

/**
 * The tags after a workout's name in a row or a header: [SideTag] for the indoor side, then Auto-detected when the
 * strap logged it by itself ([autoDetected]). Null when there's neither.
 */
internal fun workoutTags(type: Int, autoDetected: Boolean = false): (@Composable () -> Unit)? {
    val indoor = Workouts.isIndoorSide(type)
    if (!indoor && !autoDetected) return null
    return {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TagSpec.Gap)) {
            SideTag(type)
            if (autoDetected) HaloTag(TabCopy.AUTO_DETECTED)
        }
    }
}
