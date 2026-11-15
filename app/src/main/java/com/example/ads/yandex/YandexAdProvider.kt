package com.example.ads.yandex

import android.app.Activity
import android.content.Context
import com.example.ads.AdFormat
import com.example.ads.AdProvider
import com.yandex.mobile.ads.appopenad.AppOpenAd
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader

/**
 * Яндекс Mobile Ads. SDK создаётся лениво: первый вызов [preload] (после того как [com.example.ads.AdManager] разрешил
 * рекламу). Детское приложение: перед инициализацией ставится `setAgeRestricted(true)`.
 * Вызывается только из главного потока. Любая ошибка загрузки/показа не мешает игре: реклама просто пропускается.
 */
class YandexAdProvider(
    private val appOpenUnitId: String,
    private val interstitialUnitId: String
) : AdProvider {
    private var initStarted = false
    private var ready = false
    private var appOpen: AppOpenAd? = null
    private var interstitial: InterstitialAd? = null
    private var appOpenLoading = false
    private var interstitialLoading = false
    private var appOpenLoader: AppOpenAdLoader? = null
    private var interstitialLoader: InterstitialAdLoader? = null

    override fun isAvailable(format: AdFormat) = ready && when (format) {
        AdFormat.APP_OPEN -> appOpen != null
        AdFormat.INTERSTITIAL -> interstitial != null
    }

    override fun preload(activity: Activity) {
        val app = activity.applicationContext
        if (!initStarted) {
            initStarted = true
            YandexAds.setAgeRestricted(true)
            YandexAds.initialize(app) {
                ready = true
                loadAppOpen(app)
                loadInterstitial(app)
            }
            return
        }
        if (ready) {
            loadAppOpen(app)
            loadInterstitial(app)
        }
    }

    override fun show(activity: Activity, format: AdFormat, onShown: () -> Unit, onFinished: () -> Unit) {
        when (format) {
            AdFormat.APP_OPEN -> {
                val ad = appOpen ?: return onFinished()
                appOpen = null
                ad.setAdEventListener(object : AppOpenAdEventListener {
                    override fun onAdShown() = onShown()
                    override fun onAdFailedToShow(adError: AdError) { ad.setAdEventListener(null); onFinished() }
                    override fun onAdDismissed() { ad.setAdEventListener(null); onFinished() }
                    override fun onAdClicked() = Unit
                    override fun onAdImpression(impressionData: ImpressionData?) = Unit
                })
                ad.show(activity)
            }
            AdFormat.INTERSTITIAL -> {
                val ad = interstitial ?: return onFinished()
                interstitial = null
                ad.setAdEventListener(object : InterstitialAdEventListener {
                    override fun onAdShown() = onShown()
                    override fun onAdFailedToShow(adError: AdError) { ad.setAdEventListener(null); onFinished() }
                    override fun onAdDismissed() { ad.setAdEventListener(null); onFinished() }
                    override fun onAdClicked() = Unit
                    override fun onAdImpression(impressionData: ImpressionData?) = Unit
                })
                ad.show(activity)
            }
        }
        // Следующую рекламу сразу не грузим: её подгрузит AdManager.preload после закрытия, если ещё разрешено.
    }

    private fun loadAppOpen(app: Context) {
        if (appOpen != null || appOpenLoading) return
        appOpenLoading = true
        val loader = appOpenLoader ?: AppOpenAdLoader(app).also { appOpenLoader = it }
        loader.loadAd(AdRequest.Builder(appOpenUnitId).build(), object : AppOpenAdLoadListener {
            override fun onAdLoaded(appOpenAd: AppOpenAd) { appOpen = appOpenAd; appOpenLoading = false }
            // Без повторных попыток: ошибка загрузки — просто нет рекламы до следующей подгрузки.
            override fun onAdFailedToLoad(adRequestError: AdRequestError) { appOpenLoading = false }
        })
    }

    private fun loadInterstitial(app: Context) {
        if (interstitial != null || interstitialLoading) return
        interstitialLoading = true
        val loader = interstitialLoader ?: InterstitialAdLoader(app).also { interstitialLoader = it }
        loader.loadAd(AdRequest.Builder(interstitialUnitId).build(), object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: InterstitialAd) { interstitial = interstitialAd; interstitialLoading = false }
            override fun onAdFailedToLoad(adRequestError: AdRequestError) { interstitialLoading = false }
        })
    }
}
