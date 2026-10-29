package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color

/** Zone 1 (easy) to 5 (max), cool to hot; during a workout the app takes on the current one. */
internal val ZONE_COLORS = listOf(Color(0xFF8FA8FF), Color(0xFF3DDC97), Color(0xFFFFD166), Color(0xFFFF9F43), Color(0xFFFF5A5F))
internal val ZONE_NAMES: List<String>
    get() = listOf(
        org.openstrap.app.l10n.tr("Easy", "Leve"), org.openstrap.app.l10n.tr("Aerobic", "Aeróbica"), org.openstrap.app.l10n.tr("Tempo", "Ritmo"),
        org.openstrap.app.l10n.tr("Threshold", "Limiar"), org.openstrap.app.l10n.tr("Max", "Máximo"),
    )
