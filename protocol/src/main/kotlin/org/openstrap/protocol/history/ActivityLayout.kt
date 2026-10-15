package org.openstrap.protocol.history

/**
 * The activity record size (4 or 8 bytes) comes from a feature blob we don't read (02 §3.1).
 * Pick the layout under which the `mode` bytes look like real activity modes.
 */
object ActivityLayout {

    fun detect(payload: ByteArray): Int {
        if (payload.size % 8 != 0) return 4
        fun score(size: Int): Double {
            val n = payload.size / size
            if (n == 0) return 0.0
            return (0 until n).count { isPlausibleMode(payload[it * size].toInt() and 0xFF) }.toDouble() / n
        }
        // The mode byte can't always tell the layouts apart (an 8-byte record's byte 4 often looks like a
        // mode too). The Helio Strap sends 8-byte records, so a tie goes to 8. Prefer the size field when
        // the START reply gives one; this is only the fallback.
        return if (score(8) >= score(4)) 8 else 4
    }

    /** First byte: [ActivityMode] in the low nibble (high nibble is firmware state), 0xFF padding, or blank. */
    private fun isPlausibleMode(b: Int) = b == 0xFF || b == ActivityMode.BLANK || (b and 0x0F) in 0..9
}
