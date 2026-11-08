package com.pythdom.callme.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pythdom.callme.call.CallManager
import com.pythdom.callme.call.CallService
import com.pythdom.callme.data.CallStore

/** Fired by AlarmManager at the scheduled time: starts the ringing service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallScheduler.ACTION_RING) return
        val id = intent.getStringExtra(CallScheduler.EXTRA_CALL_ID) ?: return
        val call = CallStore.get(id) ?: return

        if (CallManager.isBusy()) {
            // Another fake call is ringing or in progress: try again in a minute.
            CallScheduler.schedule(context, call.copy(triggerAt = System.currentTimeMillis() + 60_000))
            return
        }
        CallStore.remove(id)
        CallService.startIncoming(context, call)
    }
}
