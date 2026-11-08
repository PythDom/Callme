package com.pythdom.callme.data

import org.json.JSONObject
import java.util.UUID

/** A fake call waiting to ring. Contact details are a snapshot; they are re-resolved at ring time. */
data class ScheduledCall(
    val id: String = UUID.randomUUID().toString(),
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String,
    val numberLabel: String,
    val photoThumbUri: String?,
    val triggerAt: Long,
    /** True for the automatic callback after a declined/missed call; it never triggers another one. */
    val isCallback: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("contactId", contactId)
        .put("lookupKey", lookupKey)
        .put("name", name)
        .put("number", number)
        .put("numberLabel", numberLabel)
        .put("photoThumbUri", photoThumbUri ?: JSONObject.NULL)
        .put("triggerAt", triggerAt)
        .put("isCallback", isCallback)

    companion object {
        fun fromJson(o: JSONObject) = ScheduledCall(
            id = o.getString("id"),
            contactId = o.getLong("contactId"),
            lookupKey = o.getString("lookupKey"),
            name = o.getString("name"),
            number = o.getString("number"),
            numberLabel = o.optString("numberLabel"),
            photoThumbUri = if (o.isNull("photoThumbUri")) null else o.getString("photoThumbUri"),
            triggerAt = o.getLong("triggerAt"),
            isCallback = o.optBoolean("isCallback"),
        )
    }
}
