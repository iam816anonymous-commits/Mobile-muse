package com.example.localagent.inventory

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.localagent.voice.VoiceEngine

class HardwareSensorManager(
    private val context: Context,
    private val voiceEngine: VoiceEngine? = null
) : SensorEventListener {

    companion object {
        private const val TAG = "HardwareSensorManager"
        private const val PROXIMITY_THRESHOLD_CM = 3.0f
        private const val HOVER_TRIGGER_DURATION_MS = 800L
    }

    private val sensorManager: SensorManager? = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var hoverStartTime: Long = 0L
    private var isHovering: Boolean = false

    fun registerProximityHook() {
        if (proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Proximity Wave-to-Listen sensor registered successfully")
        } else {
            Log.w(TAG, "Proximity sensor not available on this device")
        }
    }

    fun unregisterProximityHook() {
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Proximity Wave-to-Listen sensor unregistered")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distanceCm = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5.0f
        val isNear = distanceCm < PROXIMITY_THRESHOLD_CM && distanceCm < maxRange

        val now = System.currentTimeMillis()

        if (isNear) {
            if (!isHovering) {
                isHovering = true
                hoverStartTime = now
                Log.d(TAG, "Hand detected near proximity sensor (< 3cm)")
            }
        } else {
            if (isHovering) {
                val hoverDuration = now - hoverStartTime
                isHovering = false
                Log.d(TAG, "Hand pulled away from proximity sensor after ${hoverDuration}ms")

                if (hoverDuration >= HOVER_TRIGGER_DURATION_MS) {
                    Log.i(TAG, "Wave-to-Listen gesture triggered (> 800ms hover + pull away)")
                    voiceEngine?.startListening(
                        onResult = { query ->
                            Log.i(TAG, "Wave-to-Listen speech recognized: $query")
                            context.sendBroadcast(
                                android.content.Intent("com.localagent.EXECUTE_GOAL")
                                    .putExtra("goal_text", query)
                            )
                        },
                        onError = { error ->
                            Log.w(TAG, "Wave-to-Listen speech error: $error")
                        }
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op for proximity
    }
}
