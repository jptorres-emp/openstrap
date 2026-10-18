package org.openstrap.app.ui.workout

import androidx.compose.ui.graphics.Color

/** Zone 1 (easy) to 5 (max), cool to hot. */
internal val ZONE_COLORS = listOf(Color(0xFF8FA8FF), Color(0xFF2DD4BF), Color(0xFF3DDC97), Color(0xFFFFB547), Color(0xFFFF5A5F))
internal val ZONE_NAMES = listOf("Warm-up", "Fat burn", "Aerobic", "Anaerobic", "Maximum")

/** A drop as "−24", a rise as "+3". */
internal fun signedDrop(drop: Int) = when { drop > 0 -> "−$drop"; drop < 0 -> "+${-drop}"; else -> "0" }
