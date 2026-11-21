package org.openstrap.app.ui.glow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * What the route map page keeps: which workout's map is open ([openFor], its start), Route only and the picked
 * kilometre. A ViewModel, not `rememberSaveable`: a page's saved state is keyed by where it sits in the layout tree, and
 * the phone's stack and the wide screen's pane put it in different places, so each orientation kept its own stale copy
 * (found in #57).
 */
class RouteMapModel : ViewModel() {
    var openFor by mutableStateOf<Long?>(null)
        private set
    var routeOnly by mutableStateOf(false)
    var selected by mutableStateOf<Int?>(null)

    fun isOpen(start: Long) = openFor == start

    fun open(start: Long) {
        openFor = start
        routeOnly = false
        selected = null
    }

    fun close() {
        openFor = null
    }

    /** Picks kilometre [index], or clears it when it is the one picked. */
    fun pick(index: Int) {
        selected = if (selected == index) null else index
    }
}

/** The screen's [RouteMapModel], or null where there's nothing to keep one (a preview). */
@Composable
internal fun routeMapModel(): RouteMapModel? = LocalViewModelStoreOwner.current?.let { viewModel<RouteMapModel>(it) }
