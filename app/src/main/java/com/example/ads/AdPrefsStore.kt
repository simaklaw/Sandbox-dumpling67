package com.example.ads

import android.content.Context

/** Счётчики рекламы в SharedPreferences; сбой записи не должен ронять игру (best-effort). */
class AdPrefsStore(context: Context) : AdStateStore {
    private val prefs = context.applicationContext.getSharedPreferences("ads_prefs", Context.MODE_PRIVATE)

    override var launchCount: Int
        get() = prefs.getInt("launch_count", 0)
        set(v) { prefs.edit().putInt("launch_count", v).apply() }
    override var lastShownMs: Long
        get() = prefs.getLong("last_shown_ms", 0L)
        set(v) { prefs.edit().putLong("last_shown_ms", v).apply() }
    override var dailyDate: String
        get() = prefs.getString("daily_date", "") ?: ""
        set(v) { prefs.edit().putString("daily_date", v).apply() }
    override var dailyCount: Int
        get() = prefs.getInt("daily_count", 0)
        set(v) { prefs.edit().putInt("daily_count", v).apply() }
    override var homeShownDate: String
        get() = prefs.getString("home_shown_date", "") ?: ""
        set(v) { prefs.edit().putString("home_shown_date", v).apply() }
}
