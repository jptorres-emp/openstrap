package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.border
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.MetricColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.openstrap.app.ui.components.pressClickable

/*
 * The quiet tier as a free-form panel (L0): the solid card round content that isn't rows, like controls, a chart or a
 * note. No light, no rim and no dots, so the lit cards on a screen stay special.
 */

/** A change for the better, or a GPS fix good enough: the app's good green. */
internal val TrendGood = MetricColors.good

/** A change for the worse, or a GPS fix too poor: the app's warning amber. */
internal val TrendBad = MetricColors.warn
