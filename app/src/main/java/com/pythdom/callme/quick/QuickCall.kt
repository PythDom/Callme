package com.pythdom.callme.quick

import android.content.Context
import android.widget.Toast
import com.pythdom.callme.Config
import com.pythdom.callme.data.CallStore
import com.pythdom.callme.data.ScheduledCall
import com.pythdom.callme.scheduling.CallScheduler
import org.json.JSONObject

/** A one-tap "call me in [delayMinutes] min, by [name]" preset, used by widgets and the tile. */
data class QuickCallPreset(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String,
    val numberLabel: String,
    val photoThumbUri: String?,
    val delayMinutes: Int,
) {
    val label get() = "$name · $delayMinutes min"

    fun toJson(): JSONObject = JSONObject()
        .put("contactId", contactId)
        .put("lookupKey", lookupKey)
        .put("name", name)
        .put("number", number)
        .put("numberLabel", numberLabel)
        .put("photoThumbUri", photoThumbUri ?: JSONObject.NULL)
        .put("delayMinutes", delayMinutes)

    companion object {
        val DELAYS = listOf(1, 2, 5, 10, 15, 30)

        fun fromJson(o: JSONObject) = QuickCallPreset(
            contactId = o.getLong("contactId"),
            lookupKey = o.getString("lookupKey"),
            name = o.getString("name"),
            number = o.getString("number"),
            numberLabel = o.optString("numberLabel"),
            photoThumbUri = if (o.isNull("photoThumbUri")) null else o.getString("photoThumbUri"),
            delayMinutes = o.getInt("delayMinutes"),
        )
    }
}

/** Presets for each placed widget (by appWidgetId) and for the quick-settings tile. */
object QuickCallStore {
    private const val PREFS = "quick_calls"
    private const val TILE = "tile"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): QuickCallPreset? =
        prefs(context).getString(key, null)?.let { runCatching { QuickCallPreset.fromJson(JSONObject(it)) }.getOrNull() }

    private fun write(context: Context, key: String, preset: QuickCallPreset?) {
        prefs(context).edit().apply {
            if (preset == null) remove(key) else putString(key, preset.toJson().toString())
        }.apply()
    }

    fun widget(context: Context, widgetId: Int) = read(context, "widget_$widgetId")
    fun setWidget(context: Context, widgetId: Int, preset: QuickCallPreset?) = write(context, "widget_$widgetId", preset)

    /** Number of in-app quick preset slots on the home screen. */
    const val SLOT_COUNT = 4

    fun slot(context: Context, index: Int) = read(context, "slot_$index")
    fun setSlot(context: Context, index: Int, preset: QuickCallPreset?) = write(context, "slot_$index", preset)
    fun slots(context: Context) = (0 until SLOT_COUNT).map { slot(context, it) }

    fun tile(context: Context) = read(context, TILE)
    fun setTile(context: Context, preset: QuickCallPreset?) = write(context, TILE, preset)
}

object QuickCallTrigger {
    /** Schedules the preset's call now + delay, with a short confirmation toast. */
    fun fire(context: Context, preset: QuickCallPreset) {
        if (!CallStore.canScheduleMore()) {
            Toast.makeText(
                context,
                "${Config.MAX_PENDING_CALLS} calls already waiting. Cancel one in Callme.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        CallScheduler.schedule(
            context,
            ScheduledCall(
                contactId = preset.contactId,
                lookupKey = preset.lookupKey,
                name = preset.name,
                number = preset.number,
                numberLabel = preset.numberLabel,
                photoThumbUri = preset.photoThumbUri,
                triggerAt = System.currentTimeMillis() + preset.delayMinutes * 60_000L,
            ),
        )
        Toast.makeText(context, "Call scheduled in ${preset.delayMinutes} min", Toast.LENGTH_SHORT).show()
    }
}
