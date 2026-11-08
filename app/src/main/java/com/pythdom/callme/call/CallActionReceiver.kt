package com.pythdom.callme.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Decline / Hang up buttons of the call notification (Answer opens CallActivity directly). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DECLINE -> CallManager.decline()
            ACTION_HANG_UP -> CallManager.hangUp()
        }
    }

    companion object {
        const val ACTION_DECLINE = "com.pythdom.callme.action.DECLINE"
        const val ACTION_HANG_UP = "com.pythdom.callme.action.HANG_UP"
    }
}
