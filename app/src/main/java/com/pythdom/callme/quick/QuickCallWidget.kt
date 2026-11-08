package com.pythdom.callme.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.widget.RemoteViews
import com.pythdom.callme.R
import com.pythdom.callme.data.ContactsRepository

/** Home-screen widget: one tap = "call me in N min, by X". Each placed widget has its own preset. */
class QuickCallWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { QuickCallStore.setWidget(context, it, null) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FIRE) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            QuickCallStore.widget(context, id)?.let { QuickCallTrigger.fire(context, it) }
        }
    }

    companion object {
        private const val ACTION_FIRE = "com.pythdom.callme.action.QUICK_CALL_WIDGET"

        fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val preset = QuickCallStore.widget(context, id)
            val views = RemoteViews(context.packageName, R.layout.widget_quick_call)
            if (preset == null) {
                views.setTextViewText(R.id.widget_name, "Callme")
                views.setTextViewText(R.id.widget_delay, "Tap to set up")
                views.setImageViewResource(R.id.widget_avatar, R.drawable.ic_widget_phone)
                views.setOnClickPendingIntent(R.id.widget_root, configureIntent(context, id))
            } else {
                views.setTextViewText(R.id.widget_name, preset.name)
                views.setTextViewText(R.id.widget_delay, "Call me in ${preset.delayMinutes} min")
                val photo = ContactsRepository.loadThumbnail(context, preset.photoThumbUri)
                if (photo != null) views.setImageViewBitmap(R.id.widget_avatar, photo.circleCrop())
                else views.setImageViewResource(R.id.widget_avatar, R.drawable.ic_widget_phone)
                val fire = Intent(context, QuickCallWidget::class.java)
                    .setAction(ACTION_FIRE)
                    .setData(Uri.parse("callme://widget/$id"))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getBroadcast(context, id, fire, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            manager.updateAppWidget(id, views)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, QuickCallWidget::class.java)).forEach { update(context, manager, it) }
        }

        private fun configureIntent(context: Context, id: Int): PendingIntent =
            PendingIntent.getActivity(
                context, id,
                Intent(context, QuickCallConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .setData(Uri.parse("callme://widget-config/$id"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun Bitmap.circleCrop(): Bitmap {
            val size = minOf(width, height)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val left = (width - size) / 2
            val top = (height - size) / 2
            canvas.drawBitmap(this, Rect(left, top, left + size, top + size), Rect(0, 0, size, size), paint)
            return out
        }
    }
}
