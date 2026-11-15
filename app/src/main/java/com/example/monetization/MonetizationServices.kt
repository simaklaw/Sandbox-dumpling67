package com.example.monetization

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * Единственная точка создания сетевых и платёжных объектов. Ленивая: пока `MONETIZATION_ENABLED = false`,
 * ничего не создаётся и в сеть приложение не ходит. Создаётся не при старте, а при первом обращении
 * (каталог читает локальную БД, сеть трогает только родительский магазин).
 */
object MonetizationServices {
    @Volatile private var gatewayInstance: DirectLicenseGateway? = null

    fun gateway(context: Context): DirectLicenseGateway =
        gatewayInstance ?: synchronized(this) {
            gatewayInstance ?: DirectLicenseGateway(
                api = LicenseApiClient(BuildConfig.LICENSE_BACKEND_URL),
                verifier = EntitlementVerifier(BuildConfig.LICENSE_PUBLIC_KEY),
                dao = AppDatabase.getInstance(context).entitlementDao(),
                installId = { installId(context.applicationContext) },
                appVersion = BuildConfig.VERSION_NAME
            ).also { gatewayInstance = it }
        }

    /** Разблокированные продукты; при выключенной монетизации — пустой поток без обращения к БД. */
    fun unlockedProducts(context: Context): Flow<Set<String>> =
        if (BuildConfig.MONETIZATION_ENABLED) gateway(context).unlockedProducts else flowOf(emptySet())

    private fun installId(app: Context): String {
        val prefs = app.getSharedPreferences("monetization_prefs", Context.MODE_PRIVATE)
        prefs.getString("install_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("install_id", id).apply()
        return id
    }
}
