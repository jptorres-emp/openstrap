package org.openstrap.app.ui.dashboard

import org.openstrap.app.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openstrap.app.presentation.ChartPoint
import org.openstrap.app.ui.theme.MetricColors

/** Counts taps on the tab that's showing; each new count scrolls the [ScreenList]s on that tab to the top. */
val LocalScrollToTop = androidx.compose.runtime.compositionLocalOf { 0 }

fun List<ChartPoint>.avg(): Int? = if (isEmpty()) null else Math.round(map { it.y }.average()).toInt()
