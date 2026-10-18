package org.openstrap.app.device

import org.openstrap.protocol.auth.AuthException

/** Turns the strap's auth status codes (docs/protocol/01 §3.4) into what the user should do next. */
object PairingErrors {
    private const val WORKOUT_RUNNING = 5
    private const val NO_KEY = 7
    private const val SIGNATURE_FAILED = 8
    private const val AES_FAILED = 0x23
    private const val CIPHERTEXT_MISMATCH = 0x25
    private const val WRONG_KEY = 0x26
    private const val BINDING_PROTECTED = 0x41
    private const val CLOUD_BIND_REQUIRED = 0x81

    fun authFailure(e: AuthException): ConnectionState.Failed = when (e.status) {
        WRONG_KEY, CIPHERTEXT_MISMATCH, AES_FAILED -> ConnectionState.Failed(
            "The strap rejected this auth key. Use the key from the strap's most recent Zepp bind: every new bind " +
                "creates a new key, and keys from other devices on the account won't work.",
            keyRejected = true,
        )
        NO_KEY -> ConnectionState.Failed(
            "The strap has no key, which means it was unbound from Zepp or reset. Bind it in the Zepp app again, " +
                "get the new key, and don't unbind it afterwards.",
            keyRejected = true,
        )
        SIGNATURE_FAILED, BINDING_PROTECTED, CLOUD_BIND_REQUIRED -> ConnectionState.Failed(
            "The strap only accepts a key from a Zepp bind. Bind it in the Zepp app first, then use that bind's key.",
            keyRejected = true,
        )
        WORKOUT_RUNNING -> ConnectionState.Failed("A workout is running on the strap. End it, then tap Connect.")
        else -> ConnectionState.Failed(
            "Pairing failed (${e.message ?: "unknown error"}). Put the strap on its charger and tap Connect to try again.",
        )
    }
}
