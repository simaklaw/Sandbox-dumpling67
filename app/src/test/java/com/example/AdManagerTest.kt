package com.example

import com.example.ads.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdManagerTest {
    private class Mem : AdStateStore {
        override var launchCount = 0
        override var lastShownMs = 0L
        override var dailyDate = ""
        override var dailyCount = 0
        override var homeShownDate = ""
    }

    private class Ready : AdProvider {
        override fun isAvailable(format: AdFormat) = true
        override fun preload(activity: android.app.Activity) = Unit
        override fun show(activity: android.app.Activity, format: AdFormat, onShown: () -> Unit, onFinished: () -> Unit) = onFinished()
    }

    private class Env {
        val store = Mem()
        var now = 1_000_000_000L
        var day = "2026-11-15"
        var removed = false
        var online = true
        var roll = 0.0f // всегда «выпало»
        var provider: AdProvider = Ready()
        fun manager() = AdManager(store, provider, { removed }, { online }, { now }, { day }, { roll })
        fun launches(n: Int) = repeat(n) { manager().onAppLaunch() }
    }

    private fun skip(r: SkipReason) = AdDecision.Skip(r)

    @Test
    fun noProviderMeansNoAds() {
        val e = Env().apply { provider = NoopProvider; launches(10) }
        assertEquals(skip(SkipReason.NO_PROVIDER), e.manager().decide(AdTrigger.EXIT))
        assertEquals(skip(SkipReason.NO_PROVIDER), e.manager().decide(AdTrigger.LAUNCH))
    }

    @Test
    fun firstLaunchesAreAdFree() {
        val e = Env()
        for (i in 1..AdConfig.MIN_LAUNCH_BEFORE_FIRST_AD) {
            e.launches(1)
            assertEquals("запуск $i", skip(SkipReason.TOO_EARLY), e.manager().decide(AdTrigger.LAUNCH))
            assertEquals(skip(SkipReason.TOO_EARLY), e.manager().decide(AdTrigger.EXIT))
        }
        e.launches(1)
        assertEquals(AdDecision.Show(AdFormat.APP_OPEN), e.manager().decide(AdTrigger.LAUNCH))
    }

    @Test
    fun removeAdsBlocksEverything() {
        val e = Env().apply { launches(10); removed = true }
        assertEquals(skip(SkipReason.ADS_REMOVED), e.manager().decide(AdTrigger.EXIT))
        assertEquals(skip(SkipReason.ADS_REMOVED), e.manager().decide(AdTrigger.LAUNCH))
    }

    @Test
    fun offlineNeverShows() {
        val e = Env().apply { launches(10); online = false }
        assertEquals(skip(SkipReason.OFFLINE), e.manager().decide(AdTrigger.EXIT))
    }

    @Test
    fun globalCooldownBlocksRepeatThenReleases() {
        val e = Env().apply { launches(10) }
        val m = e.manager()
        assertEquals(AdDecision.Show(AdFormat.INTERSTITIAL), m.decide(AdTrigger.EXIT))
        m.markShown(AdTrigger.EXIT)
        assertEquals(skip(SkipReason.COOLDOWN), m.decide(AdTrigger.EXIT))      // тут же
        e.now += AdConfig.GLOBAL_COOLDOWN_MS - 1
        assertEquals(skip(SkipReason.COOLDOWN), m.decide(AdTrigger.EXIT))      // чуть раньше
        e.now += 1
        assertEquals(AdDecision.Show(AdFormat.INTERSTITIAL), m.decide(AdTrigger.EXIT))
    }

    @Test
    fun dailyLimitAndNewDayReset() {
        val e = Env().apply { launches(10) }
        val m = e.manager()
        repeat(AdConfig.DAILY_LIMIT) {
            assertTrue(m.decide(AdTrigger.EXIT) is AdDecision.Show)
            m.markShown(AdTrigger.EXIT)
            e.now += AdConfig.GLOBAL_COOLDOWN_MS
        }
        assertEquals(skip(SkipReason.DAILY_LIMIT), m.decide(AdTrigger.EXIT))
        e.day = "2026-11-16"
        assertTrue(m.decide(AdTrigger.EXIT) is AdDecision.Show)
    }

    @Test
    fun homeCardOncePerDay() {
        val e = Env().apply { launches(10) }
        val m = e.manager()
        assertTrue(m.decide(AdTrigger.LAUNCH) is AdDecision.Show)
        m.markShown(AdTrigger.LAUNCH)
        e.now += AdConfig.GLOBAL_COOLDOWN_MS * 2
        assertEquals(skip(SkipReason.HOME_DAILY_LIMIT), m.decide(AdTrigger.LAUNCH))
        assertTrue(m.decide(AdTrigger.EXIT) is AdDecision.Show) // выход из игры от этого не зависит
        e.day = "2026-11-16"
        assertTrue(m.decide(AdTrigger.LAUNCH) is AdDecision.Show)
    }

    @Test
    fun exitShowsOnlyWithProbability() {
        val e = Env().apply { launches(10) }
        e.roll = AdConfig.EXIT_PROBABILITY - 0.01f
        assertTrue(e.manager().decide(AdTrigger.EXIT) is AdDecision.Show)
        e.roll = AdConfig.EXIT_PROBABILITY
        assertEquals(skip(SkipReason.PROBABILITY), e.manager().decide(AdTrigger.EXIT))
        e.roll = 0.99f
        assertEquals(skip(SkipReason.PROBABILITY), e.manager().decide(AdTrigger.EXIT))
    }

    @Test
    fun decideDoesNotConsumeLimits_onlyMarkShownDoes() {
        val e = Env().apply { launches(10) }
        val m = e.manager()
        repeat(20) { assertTrue(m.decide(AdTrigger.EXIT) is AdDecision.Show) }
        assertEquals(0, e.store.dailyCount)
    }

    @Test
    fun limitsAreSane() {
        assertTrue(AdConfig.GLOBAL_COOLDOWN_MS >= 5 * 60 * 1000L)
        assertTrue(AdConfig.DAILY_LIMIT in 1..4)
        assertTrue(AdConfig.CLICKABILITY_DELAY_MS >= 1500L)
        assertTrue(AdConfig.CLOSE_BUTTON_MIN_DP >= 48)
        assertTrue(AdConfig.MIN_LAUNCH_BEFORE_FIRST_AD >= 2)
        assertFalse(AdConfig.EXIT_PROBABILITY > 0.5f)
    }
}
