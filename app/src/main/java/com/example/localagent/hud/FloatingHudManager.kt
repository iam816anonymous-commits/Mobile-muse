package com.example.localagent.hud

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.localagent.state.AgentStatus

class FloatingHudManager(
    private val context: Context,
    private val onAbortRequested: () -> Unit
) {

    companion object {
        private const val TAG = "FloatingHudManager"
    }

    private var windowManager: WindowManager? = null
    private var hudView: View? = null
    private var isAttached = false

    fun show() {
        if (isAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "SYSTEM_ALERT_WINDOW permission not granted. Floating HUD disabled.")
            return
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // Convert 32dp to pixels
            val density = context.resources.displayMetrics.density
            val sizePx = (32 * density).toInt()

            val params = WindowManager.LayoutParams(
                sizePx,
                sizePx,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = (16 * density).toInt()
                y = (100 * density).toInt()
            }

            hudView = View(context).apply {
                background = createCircularDrawable(Color.GRAY)
                setOnClickListener {
                    Log.w(TAG, "Floating HUD tapped. Triggering instant abort.")
                    onAbortRequested()
                }
            }

            windowManager?.addView(hudView, params)
            isAttached = true
            Log.d(TAG, "Floating HUD displayed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to display Floating HUD", e)
        }
    }

    fun updateStatus(status: AgentStatus) {
        val color = when (status) {
            AgentStatus.IDLE -> Color.GRAY
            AgentStatus.RUNNING -> Color.BLUE
            AgentStatus.COMPLETED -> Color.GREEN
            AgentStatus.HALTED, AgentStatus.FAILED -> Color.RED
        }

        hudView?.post {
            hudView?.background = createCircularDrawable(color)
        }
    }

    fun hide() {
        if (!isAttached || hudView == null) return
        try {
            windowManager?.removeView(hudView)
            isAttached = false
            hudView = null
            Log.d(TAG, "Floating HUD hidden.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide Floating HUD", e)
        }
    }

    private fun createCircularDrawable(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(2, Color.WHITE)
        }
    }
}
