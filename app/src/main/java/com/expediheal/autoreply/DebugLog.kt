package com.expediheal.autoreply

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLog {
    private const val PREFS = "wa_autoreply_debug"
    private const val KEY = "log"
    private const val MAX = 200

    private val gson = Gson()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(ctx: Context, message: String) {
        try {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY, null)
            val list: MutableList<String> = if (json != null) {
                try {
                    val type = object : TypeToken<MutableList<String>>() {}.type
                    gson.fromJson(json, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else mutableListOf()

            val timestamp = timeFmt.format(Date())
            list.add(0, "[$timestamp] $message")
            while (list.size > MAX) list.removeAt(list.size - 1)
            prefs.edit().putString(KEY, gson.toJson(list)).apply()
        } catch (_: Exception) {}
    }

    fun get(ctx: Context): List<String> {
        return try {
            val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY, null) ?: return emptyList()
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
