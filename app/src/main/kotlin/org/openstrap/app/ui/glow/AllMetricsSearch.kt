package org.openstrap.app.ui.glow

import org.openstrap.app.ui.theme.Space
import org.openstrap.app.ui.theme.Greys
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.openstrap.app.R
import org.openstrap.app.ui.theme.AppIcons
import org.openstrap.app.ui.theme.Ink
import org.openstrap.app.ui.theme.SportIcons

/* The search on All metrics (board HaloAllMetrics, "Search metrics"): the filter over the index and its field. */

/** [text] lowercased with accents and other combining marks removed, so "Résting" and "resting" are the same word. */
internal fun foldForSearch(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * The index narrowed to [query] (board HaloAllMetrics, "Search metrics"). The query's words, case and accents aside,
 * must each be found inside a word of a row's name; a query that matches a group's title the same way keeps that
 * whole group. Groups with no row left are dropped, and a blank query keeps everything.
 */
fun filterIndex(groups: List<IndexGroup>, query: String): List<IndexGroup> {
    val words = foldForSearch(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return groups
    fun hits(text: String): Boolean {
        val parts = foldForSearch(text).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return words.all { w -> parts.any { it.contains(w) } }
    }
    return groups.mapNotNull { g ->
        if (hits(g.title)) g else g.rows.filter { hits(it.name) }.takeIf { it.isNotEmpty() }?.let { IndexGroup(g.title, it) }
    }
}

/**
 * The search field of board HaloAllMetrics, looking as Choose's does: 48 dp on the Control surface with the search
 * icon and a clear mark.
 */
@Composable
internal fun MetricSearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        query, onChange, modifier.fillMaxWidth(), singleLine = true,
        textStyle = GlowType.body.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(GlowLayout.TileShape).background(Greys.Control).padding(horizontal = Space.S16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SportIcons.Search, null, tint = Ink.Secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.S8))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(
                        stringResource(R.string.glow_metrics_search), color = Ink.Secondary,
                        style = GlowType.body, maxLines = 1,
                    )
                    field()
                }
                if (query.isNotEmpty()) Icon(
                    AppIcons.X, stringResource(R.string.glow_choose_clear), tint = Ink.Secondary,
                    // 48 dp to tap, the mark drawn 16 dp, as on Choose.
                    modifier = Modifier.offset(x = 10.dp).size(48.dp).clip(CircleShape).clickable { onChange("") }.padding(Space.S16),
                )
            }
        },
    )
}
