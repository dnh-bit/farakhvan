package com.farakhvan.text.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.farakhvan.text.util.ContactItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * The "New SMS" draft.
 *
 * Kept as observable state so tab switches and rotation never lose it, and mirrored into
 * SharedPreferences so a process kill (low memory, reboot, forced stop) does not either.
 */
object Draft {
    /** 0 = group, 1 = pasted numbers, 2 = contacts. */
    var mode by mutableIntStateOf(0)
    var groupId by mutableLongStateOf(-1L)
    var pasted by mutableStateOf("")
    var message by mutableStateOf("")

    /** SIM slot chosen for this message, or -1 to use the default from settings. */
    var simSlot by mutableIntStateOf(-1)
    val contacts = mutableStateListOf<ContactItem>()

    private const val PREFS = "draft"
    private const val KEY_MODE = "mode"
    private const val KEY_GROUP = "groupId"
    private const val KEY_PASTED = "pasted"
    private const val KEY_MESSAGE = "message"
    private const val KEY_SIM = "simSlot"
    private const val KEY_CONTACTS = "contacts"

    /** Cheap change fingerprint: the persistence effect only runs when this string changes. */
    fun snapshotKey(): String {
        val sb = StringBuilder(64 + contacts.size * 24)
        sb.append(mode).append('|').append(groupId).append('|').append(simSlot).append('|')
        sb.append(pasted.length).append('|').append(message.length).append('|')
        contacts.forEach { sb.append(it.number).append(':').append(it.name).append(';') }
        return sb.toString()
    }

    fun hasContent(): Boolean =
        message.isNotBlank() || pasted.isNotBlank() || contacts.isNotEmpty() || groupId >= 0

    fun persist(context: Context) {
        try {
            val arr = JSONArray()
            contacts.forEach { c ->
                arr.put(JSONObject().put("number", c.number).put("name", c.name))
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_MODE, mode)
                .putLong(KEY_GROUP, groupId)
                .putString(KEY_PASTED, pasted)
                .putString(KEY_MESSAGE, message)
                .putInt(KEY_SIM, simSlot)
                .putString(KEY_CONTACTS, arr.toString())
                .apply()
        } catch (e: Exception) {
            // A draft that cannot be saved must never break composing a new one.
        }
    }

    fun restore(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            mode = prefs.getInt(KEY_MODE, 0).coerceIn(0, 2)
            groupId = prefs.getLong(KEY_GROUP, -1L)
            pasted = prefs.getString(KEY_PASTED, "").orEmpty()
            message = prefs.getString(KEY_MESSAGE, "").orEmpty()
            simSlot = prefs.getInt(KEY_SIM, -1)
            val raw = prefs.getString(KEY_CONTACTS, "").orEmpty()
            contacts.clear()
            if (raw.isNotBlank()) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val number = o.optString("number")
                    if (number.isBlank()) continue
                    contacts.add(ContactItem(name = o.optString("name"), number = number))
                }
            }
        } catch (e: Exception) {
            // Corrupt draft data: start clean instead of failing to open the app.
        }
    }

    fun clear(context: Context) {
        mode = 0
        groupId = -1L
        pasted = ""
        message = ""
        simSlot = -1
        contacts.clear()
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
