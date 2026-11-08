package com.pythdom.callme.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pythdom.callme.Config
import com.pythdom.callme.data.CallStore
import com.pythdom.callme.data.ScheduledCall

/**
 * Registers exact, Doze-proof alarms for pending calls.
 *
 * setExactAndAllowWhileIdle is used rather than setAlarmClock because the latter
 * shows an alarm icon in the status bar, which would give the trick away.
 */
object CallScheduler {
    const val ACTION_RING = "com.pythdom.callme.action.RING"
    const val EXTRA_CALL_ID = "call_id"

    fun schedule(context: Context, call: ScheduledCall) {
        CallStore.put(call)
        arm(context, call)
    }

    fun cancel(context: Context, id: String) {
        CallStore.remove(id)
        alarmManager(context).cancel(pendingIntent(context, id))
    }

    /** Re-arms every pending call (after boot, update, clock change); drops stale ones. */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        CallStore.all().forEach { call ->
            if (call.triggerAt < now - Config.STALE_CALL_GRACE_MS) {
                cancel(context, call.id)
            } else {
                arm(context, call)
            }
        }
    }

    fun canScheduleExact(context: Context) = alarmManager(context).canScheduleExactAlarms()

    private fun arm(context: Context, call: ScheduledCall) {
        val am = alarmManager(context)
        val pi = pendingIntent(context, call.id)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, call.triggerAt, pi)
        } else {
            // Should not happen (USE_EXACT_ALARM is auto-granted) but never lose a call.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, call.triggerAt, pi)
        }
    }

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_RING)
            .setData(Uri.parse("callme://call/$id")) // makes each PendingIntent distinct
            .putExtra(EXTRA_CALL_ID, id)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun alarmManager(context: Context) = context.getSystemService(AlarmManager::class.java)
}
