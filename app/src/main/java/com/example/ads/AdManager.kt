package com.example.ads

enum class SkipReason { ADS_REMOVED, NO_PROVIDER, OFFLINE, TOO_EARLY, COOLDOWN, DAILY_LIMIT, HOME_DAILY_LIMIT, PROBABILITY }

sealed interface AdDecision {
    data class Show(val format: AdFormat) : AdDecision
    data class Skip(val reason: SkipReason) : AdDecision
}

/** Хранилище счётчиков рекламы (SharedPreferences в приложении, память в тестах). */
interface AdStateStore {
    var launchCount: Int
    var lastShownMs: Long
    var dailyDate: String
    var dailyCount: Int
    var homeShownDate: String
}

/**
 * Единая точка решений о показе рекламы. Не показывает: при купленном `remove_ads`, без провайдера, офлайн,
 * в первые запуски, чаще кулдауна, сверх дневного лимита, на главном чаще раза в сутки, при выходе из игры —
 * с вероятностью [AdConfig.EXIT_PROBABILITY]. Показ в игровой комнате и «при успехе» не предусмотрен вообще:
 * есть только два триггера, оба вне игрового процесса.
 */
class AdManager(
    private val store: AdStateStore,
    private val provider: AdProvider,
    private val adsRemoved: () -> Boolean,
    private val online: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> String,
    private val random: () -> Float = { kotlin.random.Random.nextFloat() }
) {
    /** Вызывать один раз за запуск процесса (из `onCreate`): считает запуски. */
    fun onAppLaunch() {
        store.launchCount = store.launchCount + 1
    }

    fun decide(trigger: AdTrigger): AdDecision {
        if (adsRemoved()) return skip(SkipReason.ADS_REMOVED)
        val format = if (trigger == AdTrigger.LAUNCH) AdFormat.APP_OPEN else AdFormat.INTERSTITIAL
        if (!provider.isAvailable(format)) return skip(SkipReason.NO_PROVIDER)
        if (!online()) return skip(SkipReason.OFFLINE)
        if (store.launchCount <= AdConfig.MIN_LAUNCH_BEFORE_FIRST_AD) return skip(SkipReason.TOO_EARLY)
        val t = now()
        if (store.lastShownMs != 0L && t - store.lastShownMs < AdConfig.GLOBAL_COOLDOWN_MS) return skip(SkipReason.COOLDOWN)
        val day = today()
        val dailyCount = if (store.dailyDate == day) store.dailyCount else 0
        if (dailyCount >= AdConfig.DAILY_LIMIT) return skip(SkipReason.DAILY_LIMIT)
        when (trigger) {
            AdTrigger.LAUNCH -> if (store.homeShownDate == day) return skip(SkipReason.HOME_DAILY_LIMIT)
            AdTrigger.EXIT -> if (random() >= AdConfig.EXIT_PROBABILITY) return skip(SkipReason.PROBABILITY)
        }
        return AdDecision.Show(format)
    }

    /**
     * Подгрузка рекламы заранее. Не раньше, чем будет разрешено показывать: не при `remove_ads`, не офлайн и не в первые
     * запуски (в эти запуски SDK вообще не создаётся). Вызывать после первого кадра, не из `onCreate`.
     */
    fun preload(activity: android.app.Activity) {
        if (adsRemoved() || !online() || store.launchCount <= AdConfig.MIN_LAUNCH_BEFORE_FIRST_AD) return
        provider.preload(activity)
    }

    /** Единственный путь показа: решение [decide], показ через провайдер, учёт лимитов по факту показа. */
    fun tryShow(activity: android.app.Activity, trigger: AdTrigger): Boolean {
        val d = decide(trigger) as? AdDecision.Show ?: return false
        provider.show(activity, d.format, onShown = { markShown(trigger) }, onFinished = { preload(activity) })
        return true
    }

    /** Вызывать, когда реклама реально показана на экране. */
    fun markShown(trigger: AdTrigger) {
        val day = today()
        store.dailyCount = (if (store.dailyDate == day) store.dailyCount else 0) + 1
        store.dailyDate = day
        store.lastShownMs = now()
        if (trigger == AdTrigger.LAUNCH) store.homeShownDate = day
    }

    private fun skip(r: SkipReason) = AdDecision.Skip(r)
}
