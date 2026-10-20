package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SoundEffectPlayer
import com.example.data.GameRepository
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = GameRepository(context)
    val soundPlayer = SoundEffectPlayer(context)
    val musicManager = com.example.audio.BackgroundMusicManager().apply { isEnabled = false }
    val tiltSensorManager = com.example.sensor.TiltSensorManager(context)
    val viewModel = com.example.ui.viewmodel.PlaygroundViewModel(
        repository = repository,
        soundPlayer = soundPlayer,
        musicManager = musicManager,
        tiltSensorManager = tiltSensorManager
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        MainAppScreen(
            viewModel = viewModel,
            repository = repository,
            soundPlayer = soundPlayer
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
