package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceTilt(
    val pitch: Float = 0f, // Forward / Backward tilt (-1..1)
    val roll: Float = 0f,  // Left / Right tilt (-1..1)
    val magnitude: Float = 0f
)

/**
 * Low-pass filtered accelerometer listener providing smooth tilt physics for squishy objects and characters.
 */
class TiltSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = try {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    } catch (_: Exception) {
        null
    }
    private val accelerometer = try {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    } catch (_: Exception) {
        null
    }

    private val _tiltState = MutableStateFlow(DeviceTilt())
    val tiltState: StateFlow<DeviceTilt> = _tiltState.asStateFlow()

    private var smoothedRoll = 0f
    private var smoothedPitch = 0f
    private var lastEmittedRoll = 0f
    private var lastEmittedPitch = 0f
    private val filterAlpha = 0.14f // Smooth low-pass filter
    private val deadbandThreshold = 0.012f

    fun startListening() {
        try {
            if (accelerometer != null && sensorManager != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
    }

    fun stopListening() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0] // X-axis: Roll (tilt left/right)
            val ay = event.values[1] // Y-axis: Pitch (tilt top/bottom)

            // Normalize values (-9.8 to +9.8 -> -1.0 to +1.0)
            val targetRoll = (-ax / 9.8f).coerceIn(-1.0f, 1.0f)
            val targetPitch = (ay / 9.8f).coerceIn(-1.0f, 1.0f)

            // Low-pass filter to remove jitter and provide smooth physics feel
            smoothedRoll += (targetRoll - smoothedRoll) * filterAlpha
            smoothedPitch += (targetPitch - smoothedPitch) * filterAlpha

            // Deadband filter: only emit state flow updates if tilt has meaningfully changed
            if (kotlin.math.abs(smoothedRoll - lastEmittedRoll) >= deadbandThreshold ||
                kotlin.math.abs(smoothedPitch - lastEmittedPitch) >= deadbandThreshold
            ) {
                lastEmittedRoll = smoothedRoll
                lastEmittedPitch = smoothedPitch
                val magnitude = (smoothedRoll * smoothedRoll + smoothedPitch * smoothedPitch).coerceIn(0f, 1f)

                _tiltState.value = DeviceTilt(
                    roll = smoothedRoll,
                    pitch = smoothedPitch,
                    magnitude = magnitude
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
