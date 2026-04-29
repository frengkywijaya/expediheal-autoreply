package com.expediheal.autoreply

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object Settings {
    private const val PREFS_NAME = "wa_autoreply_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TEMPLATES = "templates"
    private const val KEY_DELAY_MIN_SECONDS = "delay_min_seconds"
    private const val KEY_DELAY_MAX_SECONDS = "delay_max_seconds"
    private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
    private const val KEY_REPLIED_USERS = "replied_users_v2"

    private val gson = Gson()

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getTemplates(ctx: Context): MutableList<String> {
        val json = prefs(ctx).getString(KEY_TEMPLATES, null) ?: return defaultTemplates()
        return try {
            val type = object : TypeToken<MutableList<String>>() {}.type
            gson.fromJson(json, type) ?: defaultTemplates()
        } catch (e: Exception) {
            defaultTemplates()
        }
    }

    fun setTemplates(ctx: Context, templates: List<String>) {
        prefs(ctx).edit().putString(KEY_TEMPLATES, gson.toJson(templates)).apply()
    }

    private fun defaultTemplates(): MutableList<String> = mutableListOf(
        "Halo, terima kasih sudah menghubungi. Saya akan balas secepatnya.",
        "Hi! Maaf saya sedang tidak bisa balas, akan saya hubungi nanti.",
        "Pesan kamu sudah saya terima, mohon ditunggu ya.",
        "Hi, saya sedang sibuk sebentar — akan saya balas segera.",
        "Terima kasih, saya akan respon segera setelah ada waktu."
    )

    fun getDelayMinSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_DELAY_MIN_SECONDS, 6)
    fun getDelayMaxSeconds(ctx: Context): Int = prefs(ctx).getInt(KEY_DELAY_MAX_SECONDS, 10)

    fun setDelayRange(ctx: Context, minSec: Int, maxSec: Int) {
        prefs(ctx).edit()
            .putInt(KEY_DELAY_MIN_SECONDS, minSec)
            .putInt(KEY_DELAY_MAX_SECONDS, maxSec)
            .apply()
    }

    fun getCooldownMinutes(ctx: Context): Int = prefs(ctx).getInt(KEY_COOLDOWN_MINUTES, 30)
    fun setCooldownMinutes(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putInt(KEY_COOLDOWN_MINUTES, minutes).apply()
    }

    fun getRepliedUsers(ctx: Context): MutableMap<String, Long> {
        val json = prefs(ctx).getString(KEY_REPLIED_USERS, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, Long>>() {}.type
            gson.fromJson(json, type) ?: mutableMapOf()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun setRepliedUsers(ctx: Context, map: Map<String, Long>) {
        prefs(ctx).edit().putString(KEY_REPLIED_USERS, gson.toJson(map)).apply()
    }

    fun markReplied(ctx: Context, userKey: String) {
        val map = getRepliedUsers(ctx)
        map[userKey] = System.currentTimeMillis()
        // Prune entries older than 24 hours to keep prefs small.
        val cutoff = System.currentTimeMillis() - (24L * 60 * 60 * 1000)
        val pruned = map.filterValues { it > cutoff }.toMutableMap()
        setRepliedUsers(ctx, pruned)
    }

    fun isInCooldown(ctx: Context, userKey: String): Boolean {
        val lastReply = getRepliedUsers(ctx)[userKey] ?: return false
        val cooldownMs = getCooldownMinutes(ctx) * 60L * 1000L
        return (System.currentTimeMillis() - lastReply) < cooldownMs
    }
}
