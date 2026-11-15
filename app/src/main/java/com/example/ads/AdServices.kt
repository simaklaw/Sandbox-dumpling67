package com.example.ads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Единственное место сборки рекламы. Ленивое: ничего не создаётся, пока не вызван [manager]; SDK в `Application` нет.
 * Пока реального SDK нет: в release провайдер [NoopProvider], в debug (при `ADS_ENABLED`) тестовая заглушка.
 */
object AdServices {
    /** true, пока права не прочитаны или куплен `remove_ads`: тогда рекламы нет. Обновляет MainActivity. */
    @Volatile var adsRemoved: Boolean = true

    @Volatile private var instance: AdManager? = null

    fun provider(): AdProvider =
        if (BuildConfig.ADS_ENABLED && BuildConfig.DEBUG) DebugAdProvider else NoopProvider

    fun manager(context: Context): AdManager =
        instance ?: synchronized(this) {
            instance ?: AdManager(
                store = AdPrefsStore(context),
                provider = provider(),
                adsRemoved = { adsRemoved },
                online = { isOnline(context.applicationContext) },
                today = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
            ).also { instance = it }
        }

    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: SecurityException) {
        false
    }
}
