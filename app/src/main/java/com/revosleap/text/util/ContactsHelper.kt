package com.revosleap.text.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactItem(val name: String, val number: String)

object ContactsHelper {

    /** All phone numbers in the contact book (deduplicated by canonical number), sorted by name. */
    @SuppressLint("MissingPermission")
    suspend fun load(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<ContactItem>()
        val seen = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC"
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val number = cursor.getString(numIdx).orEmpty()
                if (number.isBlank()) continue
                val key = PhoneNormalizer.normalize(number).canonical
                if (seen.add(key)) {
                    result.add(ContactItem(cursor.getString(nameIdx).orEmpty(), number))
                }
            }
        }
        result
    }

    /** canonical number -> display name; empty when the contacts permission is missing. */
    suspend fun nameMap(context: Context): Map<String, String> {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return emptyMap()
        return try {
            val map = HashMap<String, String>()
            for (c in load(context)) {
                val p = PhoneNormalizer.normalize(c.number)
                if (p.valid && c.name.isNotBlank()) map[p.canonical] = c.name
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
