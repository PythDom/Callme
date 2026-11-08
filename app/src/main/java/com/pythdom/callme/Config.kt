package com.pythdom.callme

/** Behaviour constants agreed in SPEC.md §8. */
object Config {
    /** Maximum number of user-scheduled calls waiting at once (callbacks don't count). */
    const val MAX_PENDING_CALLS = 3

    /** How long the phone rings before the call counts as missed. */
    const val RING_DURATION_MS = 20_000L

    /** Delay before the single callback after a declined or missed call. */
    const val CALLBACK_DELAY_MS = 90_000L

    /** A call whose time passed more than this long ago (e.g. phone was off) is dropped. */
    const val STALE_CALL_GRACE_MS = 60_000L
}
