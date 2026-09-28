package com.example.localagent.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

class GestureExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "GestureExecutor"
        const val DEFAULT_DISPLAY_WIDTH = 720f
        const val DEFAULT_DISPLAY_HEIGHT = 1440f
    }

    @JvmOverloads
    fun tap(x: Float, y: Float, callback: AccessibilityService.GestureResultCallback? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        Log.d(TAG, "Dispatching tap gesture at ($x, $y)")
        return service.dispatchGesture(gesture, callback, null)
    }

    @JvmOverloads
    fun doubleTap(x: Float, y: Float, callback: AccessibilityService.GestureResultCallback? = null): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke1 = GestureDescription.StrokeDescription(path, 0, 50)
        val stroke2 = GestureDescription.StrokeDescription(path, 100, 50)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
        Log.d(TAG, "Dispatching double tap gesture at ($x, $y)")
        return service.dispatchGesture(gesture, callback, null)
    }

    @JvmOverloads
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        callback: AccessibilityService.GestureResultCallback? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        Log.d(TAG, "Dispatching swipe gesture from ($startX, $startY) to ($endX, $endY)")
        val dispatched = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i("GESTURE", "Swipe physically executed")
                callback?.onCompleted(gestureDescription)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w("GESTURE", "Swipe cancelled by system")
                callback?.onCancelled(gestureDescription)
            }
        }, null)
        try {
            val broadcastMethod = service.javaClass.getMethod("broadcastTelemetryLog", String::class.java, String::class.java)
            broadcastMethod.invoke(service, "GESTURE", "Coordinate swipe dispatched from ($startX, $startY) to ($endX, $endY) -> Dispatched: $dispatched")
        } catch (e: Exception) {
            Log.d("GESTURE", "Coordinate swipe dispatched from ($startX, $startY) to ($endX, $endY) -> Dispatched: $dispatched")
        }
        return dispatched
    }
}
