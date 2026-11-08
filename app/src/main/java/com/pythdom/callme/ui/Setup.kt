package com.pythdom.callme.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.pythdom.callme.scheduling.CallScheduler

/** One thing the app needs to work reliably, and how to fix it if missing. */
data class Requirement(
    val title: String,
    val detail: String,
    val ok: Boolean,
    /** Runtime permission to request, or null if [settingsIntent] should be opened instead. */
    val permission: String? = null,
    val settingsIntent: Intent? = null,
    /** Blocking requirements prevent scheduling; others are strong recommendations. */
    val blocking: Boolean = true,
)

fun requirements(context: Context): List<Requirement> {
    val pkg = Uri.parse("package:${context.packageName}")
    val nm = context.getSystemService(NotificationManager::class.java)
    val pm = context.getSystemService(PowerManager::class.java)
    fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    return listOf(
        Requirement(
            "Contacts",
            "To list your favorites and show their photo.",
            granted(Manifest.permission.READ_CONTACTS),
            permission = Manifest.permission.READ_CONTACTS,
        ),
        Requirement(
            "Notifications",
            "The incoming call is shown through a call notification.",
            granted(Manifest.permission.POST_NOTIFICATIONS),
            permission = Manifest.permission.POST_NOTIFICATIONS,
        ),
        Requirement(
            "Full-screen call screen",
            "Lets the call screen appear over the lock screen.",
            nm.canUseFullScreenIntent(),
            settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
        ),
        Requirement(
            "Exact alarms",
            "So the call rings at exactly the chosen time.",
            CallScheduler.canScheduleExact(context),
            settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg),
        ),
        Requirement(
            "Battery: Unrestricted",
            "Samsung may put the app to sleep and skip the call. Allow it to run in the background.",
            pm.isIgnoringBatteryOptimizations(context.packageName),
            settingsIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg),
            blocking = false,
        ),
    )
}
