package com.pythdom.callme.data

import android.content.Context
import android.content.SharedPreferences
import com.pythdom.callme.Config
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Persists pending calls. At most a handful of entries, so a JSON list in
 * SharedPreferences is plenty and keeps the app dependency-free.
 */
object CallStore {
    private const val PREFS = "calls"
    private const val KEY = "pending"

    private lateinit var prefs: SharedPreferences
    private val _calls = MutableStateFlow<List<ScheduledCall>>(emptyList())
    val calls: StateFlow<List<ScheduledCall>> = _calls.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _calls.value = load()
    }

    @Synchronized
    fun get(id: String): ScheduledCall? = _calls.value.firstOrNull { it.id == id }

    @Synchronized
    fun all(): List<ScheduledCall> = _calls.value

    /** Number of user-scheduled (non-callback) calls currently waiting. */
    fun pendingRegularCount(): Int = _calls.value.count { !it.isCallback }

    fun canScheduleMore(): Boolean = pendingRegularCount() < Config.MAX_PENDING_CALLS

    @Synchronized
    fun put(call: ScheduledCall) {
        val list = _calls.value.filterNot { it.id == call.id } + call
        commit(list.sortedBy { it.triggerAt })
    }

    @Synchronized
    fun remove(id: String): ScheduledCall? {
        val existing = get(id) ?: return null
        commit(_calls.value.filterNot { it.id == id })
        return existing
    }

    private fun commit(list: List<ScheduledCall>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        // commit() (not apply()) so the state survives the process being killed right after.
        prefs.edit().putString(KEY, arr.toString()).commit()
        _calls.value = list
    }

    private fun load(): List<ScheduledCall> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { ScheduledCall.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedBy { it.triggerAt }
    }
}
