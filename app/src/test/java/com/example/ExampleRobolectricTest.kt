package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Детская площадка", appName)

    val report = com.example.audio.SoundAuditManager.runAudit(context)
    assertEquals(0, report.missingP1.size)
    assertEquals(0, report.missingP2.size)
    assertEquals(0, report.missingP3.size)
    assertEquals(report.totalFilesExpected, report.totalFilesFound)
  }

  @Test
  fun `launch MainActivity without crash`() {
    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).create()
    org.junit.Assert.assertNotNull(controller.get())
    controller.destroy()
  }

  @Test
  fun `verify SoundEffectPlayer and SoundManager initialization without crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundPlayer = com.example.audio.SoundEffectPlayer(context)
    val soundManager = com.example.shared.SoundManager.getInstance(context, soundPlayer)
    org.junit.Assert.assertNotNull(soundPlayer)
    org.junit.Assert.assertNotNull(soundManager)
    
    // Verify toggling does not crash
    soundPlayer.soundEnabled = false
    assertEquals(false, soundPlayer.soundEnabled)
    soundPlayer.soundEnabled = true
    assertEquals(true, soundPlayer.soundEnabled)
  }

  @Test
  fun `verify RunnerSoundEffectsManager initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundPlayer = com.example.audio.SoundEffectPlayer(context)
    val runnerSoundManager = com.example.audio.RunnerSoundEffectsManager(context, soundPlayer)
    org.junit.Assert.assertNotNull(runnerSoundManager)
    runnerSoundManager.playJump()
    runnerSoundManager.playBounce(com.example.physics.runner.RunnerEntityType.TRAMPOLINE, impactSpeed = 0.5f)
  }

  @Test
  fun `verify RunnerSoundEffectsManager audio features and toggleSound`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val soundPlayer = com.example.audio.SoundEffectPlayer(context)
    val soundManager = com.example.shared.SoundManager.getInstance(context, soundPlayer)
    val runnerSoundManager = com.example.audio.RunnerSoundEffectsManager(context, soundPlayer, soundManager)

    // Initial state
    assertEquals(true, runnerSoundManager.isSoundCurrentlyEnabled)

    // Toggle mute
    val mutedResult = runnerSoundManager.toggleSound()
    assertEquals(false, mutedResult)
    assertEquals(false, runnerSoundManager.isSoundCurrentlyEnabled)

    // Toggle unmute
    val unmutedResult = runnerSoundManager.toggleSound()
    assertEquals(true, unmutedResult)
    assertEquals(true, runnerSoundManager.isSoundCurrentlyEnabled)

    // Test cartoon audio actions
    runnerSoundManager.playDoubleJumpSpin()
    runnerSoundManager.playJump()
    runnerSoundManager.playJump(isDoubleJump = true)
    runnerSoundManager.playBounce(com.example.physics.runner.RunnerEntityType.TRAMPOLINE)
    runnerSoundManager.playBounce(com.example.physics.runner.RunnerEntityType.BUMPER)
    runnerSoundManager.playBubblyItemCollect(comboStreak = 1)
    runnerSoundManager.playCollect(com.example.physics.runner.RunnerEntityType.COLLECTIBLE_COIN, comboStreak = 3)
    runnerSoundManager.playCollectCoin(comboStreak = 5)
    runnerSoundManager.playCollectDumpling(comboStreak = 2)
    runnerSoundManager.playObstacleSmash()
    runnerSoundManager.playFootstep(isLeftFoot = true)
    runnerSoundManager.playFootstep(isLeftFoot = false)
    runnerSoundManager.playLand(1.0f)
    runnerSoundManager.playPowerUpShield()
    runnerSoundManager.playShieldBreak()
    runnerSoundManager.playPowerUpMagnet()
    runnerSoundManager.playIceSlide()
    runnerSoundManager.playJellyBounce()
    runnerSoundManager.playSteamWhoosh()
    runnerSoundManager.playStickerUnlockCelebration()
  }

  @Test
  fun `verify PelmenRunner biomes progression and interactive objects`() {
    val engine = com.example.physics.runner.PelmenRunnerPhysicsEngine()

    // Initial biome is KITCHEN
    assertEquals(com.example.physics.runner.RunnerBiome.KITCHEN, engine.currentBiome)

    // Check biome calculation by position
    assertEquals(com.example.physics.runner.RunnerBiome.KITCHEN, engine.getBiomeForPosition(50f))
    assertEquals(com.example.physics.runner.RunnerBiome.ICE_FREEZER, engine.getBiomeForPosition(200f))
    assertEquals(com.example.physics.runner.RunnerBiome.STEAM_CLOUDS, engine.getBiomeForPosition(400f))

    // Step physics and verify no crash
    engine.step(0.016f)
    org.junit.Assert.assertNotNull(engine.heroBody)
  }

  @Test
  fun `verify GameRepository sticker seeding and distance unlocking`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = com.example.data.GameRepository(context)

    kotlinx.coroutines.runBlocking {
      // 50m unlocks the first sticker (Первый шажок)
      val unlockedAt50 = repo.checkAndUnlockDistanceStickers(50)
      org.junit.Assert.assertTrue(unlockedAt50.any { it.id == "stk_first_step" })

      // 160m unlocks ice crystal in freezer biome
      val unlockedAt160 = repo.checkAndUnlockDistanceStickers(160)
      org.junit.Assert.assertTrue(unlockedAt160.any { it.id == "stk_ice_crystal" })
    }
  }

  @Test
  fun `verify PelmenRunnerPhysicsEngine shield and magnet powerup mechanics`() {
    var shieldAbsorbedCount = 0
    val engine = com.example.physics.runner.PelmenRunnerPhysicsEngine(
      onShieldAbsorb = { shieldAbsorbedCount++ }
    )

    // Initial state
    assertEquals(false, engine.isShieldActive)
    assertEquals(false, engine.isMagnetActive)
    assertEquals(0f, engine.shieldTimer, 0.001f)
    assertEquals(0f, engine.magnetTimer, 0.001f)

    // Activate shield
    engine.activateShield(12f)
    assertEquals(true, engine.isShieldActive)
    assertEquals(12f, engine.shieldTimer, 0.001f)

    // Advance physics step
    engine.step(1.0f)
    assertEquals(true, engine.isShieldActive)
    org.junit.Assert.assertTrue(engine.shieldTimer < 11.5f && engine.shieldTimer > 10.5f)

    // Deactivate shield
    engine.deactivateShield()
    assertEquals(false, engine.isShieldActive)
    assertEquals(0f, engine.shieldTimer, 0.001f)

    // Activate magnet
    engine.activateMagnet(10f)
    assertEquals(true, engine.isMagnetActive)
    assertEquals(10f, engine.magnetTimer, 0.001f)

    // Advance step
    engine.step(2.0f)
    assertEquals(true, engine.isMagnetActive)
    org.junit.Assert.assertTrue(engine.magnetTimer < 8.5f && engine.magnetTimer > 7.5f)

    // Deactivate magnet
    engine.deactivateMagnet()
    assertEquals(false, engine.isMagnetActive)
    assertEquals(0f, engine.magnetTimer, 0.001f)

    // Reset clears powerups
    engine.activateShield(5f)
    engine.activateMagnet(5f)
    engine.reset()
    assertEquals(false, engine.isShieldActive)
    assertEquals(false, engine.isMagnetActive)
  }
}

