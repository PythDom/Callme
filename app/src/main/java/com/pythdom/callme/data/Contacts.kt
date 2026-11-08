package com.pythdom.callme.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telephony.PhoneNumberUtils
import java.util.Locale

data class PhoneNumber(val number: String, val label: String)

data class FavoriteContact(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val photoThumbUri: String?,
    val numbers: List<PhoneNumber>,
)

/** What the call screen shows. */
data class CallerInfo(
    val name: String,
    val number: String,
    val numberLabel: String,
    val photo: Bitmap?,
)

object ContactsRepository {

    /** Starred contacts that have at least one phone number, sorted by name. */
    fun favorites(context: Context): List<FavoriteContact> {
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_SUPER_PRIMARY,
        )
        val byContact = LinkedHashMap<Long, FavoriteContact>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            projection,
            "${Phone.STARRED} = 1",
            null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC, ${Phone.IS_SUPER_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(4) ?: continue
                val label = Phone.getTypeLabel(context.resources, c.getInt(5), c.getString(6)).toString()
                val existing = byContact[id]
                val phone = PhoneNumber(number, label)
                if (existing == null) {
                    byContact[id] = FavoriteContact(
                        contactId = id,
                        lookupKey = c.getString(1) ?: "",
                        name = c.getString(2) ?: number,
                        photoThumbUri = c.getString(3),
                        numbers = listOf(phone),
                    )
                } else if (existing.numbers.none { PhoneNumberUtils.areSamePhoneNumber(it.number, number, country()) }) {
                    byContact[id] = existing.copy(numbers = existing.numbers + phone)
                }
            }
        }
        return byContact.values.toList()
    }

    /**
     * Re-reads the contact at ring time so a changed name/photo is current;
     * falls back to the stored snapshot if the contact was deleted.
     */
    fun resolveCaller(context: Context, call: ScheduledCall): CallerInfo {
        val cr = context.contentResolver
        var name = call.name
        var photo: Bitmap? = null
        runCatching {
            val lookupUri = ContactsContract.Contacts.getLookupUri(call.contactId, call.lookupKey)
            val contactUri = ContactsContract.Contacts.lookupContact(cr, lookupUri)
            if (contactUri != null) {
                cr.query(contactUri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0)?.let { n -> name = n } }
                ContactsContract.Contacts.openContactPhotoInputStream(cr, contactUri, true)
                    ?.use { photo = BitmapFactory.decodeStream(it) }
            }
        }
        return CallerInfo(name, formatNumber(call.number), call.numberLabel, photo)
    }

    fun loadThumbnail(context: Context, uri: String?): Bitmap? {
        if (uri == null) return null
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun formatNumber(number: String): String =
        PhoneNumberUtils.formatNumber(number, country()) ?: number

    private fun country(): String = Locale.getDefault().country.ifEmpty { "US" }
}
