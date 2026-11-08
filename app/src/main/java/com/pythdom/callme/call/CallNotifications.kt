package com.pythdom.callme.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.pythdom.callme.R
import com.pythdom.callme.data.CallerInfo
import com.pythdom.callme.ui.CallActivity
import com.pythdom.callme.ui.MainActivity

object CallNotifications {
    private const val CHANNEL_INCOMING = "incoming_call"
    private const val CHANNEL_ONGOING = "ongoing_call"
    private const val CHANNEL_MISSED = "missed_call"

    const val CALL_NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    // Ringtone and vibration are driven by CallService according to the ringer mode.
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
                NotificationChannel(CHANNEL_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            ),
        )
    }

    fun callActivityIntent(context: Context): Intent =
        Intent(context, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)

    fun incoming(context: Context, caller: CallerInfo): Notification {
        val fullScreen = PendingIntent.getActivity(
            context, 0, callActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(caller.name)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setStyle(
                Notification.CallStyle.forIncomingCall(
                    person(caller),
                    action(context, CallActionReceiver.ACTION_DECLINE, 1),
                    // An activity PendingIntent: Android blocks activity starts from notification broadcasts.
                    PendingIntent.getActivity(
                        context, 2,
                        callActivityIntent(context).putExtra(CallActivity.EXTRA_ANSWER, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ),
            )
            .build()
    }

    fun ongoing(context: Context, caller: CallerInfo, connectedAtWallClock: Long): Notification {
        val open = PendingIntent.getActivity(
            context, 0, callActivityIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_call_notification)
            .setContentTitle(caller.name)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(open)
            .setWhen(connectedAtWallClock)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setStyle(
                Notification.CallStyle.forOngoingCall(
                    person(caller),
                    action(context, CallActionReceiver.ACTION_HANG_UP, 3),
                ),
            )
            .build()
    }

    fun postMissed(context: Context, caller: CallerInfo) {
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_missed_call)
            .setContentTitle("Missed call")
            .setContentText(caller.name)
            .setSubText(caller.numberLabel.ifEmpty { null })
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        caller.photo?.let { builder.setLargeIcon(Icon.createWithBitmap(it.scaledForNotification())) }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
    }

    private fun person(caller: CallerInfo): Person {
        val b = Person.Builder().setName(caller.name).setImportant(true)
        caller.photo?.let { b.setIcon(Icon.createWithBitmap(it.scaledForNotification())) }
        return b.build()
    }

    /** Keeps notification payloads small (binder transactions are size-limited). */
    private fun Bitmap.scaledForNotification(max: Int = 256): Bitmap {
        if (width <= max && height <= max) return this
        val scale = max.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }

    private fun action(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
