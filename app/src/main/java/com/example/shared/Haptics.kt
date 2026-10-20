package com.example.shared

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class Haptics(private val context: Context? = null) {

    private val vibrator: Vibrator? = context?.let { ctx ->
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    fun vibrateShort() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(15)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun vibratePop() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(20, 180))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(20)
                    }
                }
            }
        } catch (_: Exception) {
            vibrateShort()
        }
    }

    fun vibrateSquish() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(10, 25, 10), intArrayOf(100, 255, 100), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(40)
                    }
                }
            }
        } catch (_: Exception) {
            vibrateShort()
        }
    }

    fun vibrateWater() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(5, 15, 8), intArrayOf(60, 140, 80), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(25)
                    }
                }
            }
        } catch (_: Exception) {
            vibrateShort()
        }
    }

    /**
     * Crisp, punchy tactile mechanical click feedback for toddler dialer button presses.
     * Uses Android Q+ EFFECT_CLICK or custom calibrated 22ms waveform on older devices.
     */
    fun vibrateDialerKey() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(24, 220))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(24)
                    }
                }
            }
        } catch (_: Exception) {
            vibrateShort()
        }
    }

    /**
     * Bouncy double-pulse haptic pattern for placing a call.
     */
    fun vibrateDialerCall() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 45), intArrayOf(0, 190, 0, 255), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(60)
                    }
                }
            }
        } catch (_: Exception) {
            vibratePop()
        }
    }

    /**
     * Descending soft haptic squish pattern for hanging up the call.
     */
    fun vibrateDialerHangup() {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 40, 20), intArrayOf(0, 220, 0, 120), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(40)
                    }
                }
            }
        } catch (_: Exception) {
            vibrateSquish()
        }
    }

    fun click() = vibrateShort()

    fun vibrate(durationMs: Long) {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(durationMs)
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}
