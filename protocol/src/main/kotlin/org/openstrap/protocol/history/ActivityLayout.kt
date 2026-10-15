package org.openstrap.protocol.history

/**
 * The activity record size (4 or 8 bytes) comes from a feature blob we don't read (02 §3.1).
 * Pick the layout under which the `mode` bytes look like real activity modes.
 */
object ActivityLayout {
    private val VALID_MODES = (0..9).toSet() + ActivityMode.BLANK

    fun detect(payload: ByteArray): Int {
        if (payload.size % 8 != 0) return 4
        fun score(size: Int): Double {
            val n = payload.size / size
            if (n == 0) return 0.0
            return (0 until n).count { (payload[it * size].toInt() and 0xFF) in VALID_MODES }.toDouble() / n
        }
        // Prefer 4 unless the 8-byte reading is clearly more plausible.
        return if (score(8) > score(4) + 0.2) 8 else 4
    }
}
