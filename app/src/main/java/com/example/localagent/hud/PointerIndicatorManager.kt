package com.example.localagent.hud

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

object PointerIndicatorManager {

    private const val TAG = "PointerIndicator"

    private var windowManager: WindowManager? = null
    private var rootOverlay: FrameLayout? = null
    private var cursorIcon: ImageView? = null
    private var clickRipple: View? = null
    private var actionBadge: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        if (rootOverlay != null) return
        windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        rootOverlay = FrameLayout(context.applicationContext)

        // 1. Mouse Cursor Icon (Arrow)
        cursorIcon = ImageView(context.applicationContext).apply {
            layoutParams = FrameLayout.LayoutParams(42, 42).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setImageResource(android.R.drawable.arrow_up_float)
            setColorFilter(Color.parseColor("#00E5FF")) // Cyberpunk cyan
            translationX = -100f
            translationY = -100f
        }

        // 2. Click Ripple Ring
        clickRipple = View(context.applicationContext).apply {
            layoutParams = FrameLayout.LayoutParams(64, 64)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(4, Color.parseColor("#FFD600")) // Bright yellow pulse
                setColor(Color.parseColor("#33FFD600"))
            }
            alpha = 0f
        }

        // 3. Floating Keystroke / Action Badge
        actionBadge = TextView(context.applicationContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setPadding(16, 8, 16, 8)
            background = GradientDrawable().apply {
                cornerRadius = 14f
                setColor(Color.parseColor("#CC111827"))
                setStroke(2, Color.parseColor("#00E5FF"))
            }
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            alpha = 0f
        }

        rootOverlay?.addView(clickRipple)
        rootOverlay?.addView(cursorIcon)
        rootOverlay?.addView(actionBadge)

        try {
            windowManager?.addView(rootOverlay, params)
            Log.i(TAG, "PointerIndicatorManager mounted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add pointer overlay", e)
        }
    }

    fun showClick(x: Float, y: Float) {
        mainHandler.post {
            cursorIcon?.let {
                it.translationX = x
                it.translationY = y
                it.alpha = 1f
            }
            clickRipple?.let {
                it.translationX = x - 32
                it.translationY = y - 32
                it.scaleX = 0.3f
                it.scaleY = 0.3f
                it.alpha = 1f

                val scaleX = ObjectAnimator.ofFloat(it, "scaleX", 0.3f, 1.8f)
                val scaleY = ObjectAnimator.ofFloat(it, "scaleY", 0.3f, 1.8f)
                val fade = ObjectAnimator.ofFloat(it, "alpha", 1f, 0f)

                AnimatorSet().apply {
                    playTogether(scaleX, scaleY, fade)
                    duration = 350
                    start()
                }
            }
        }
    }

    fun showKeystroke(text: String, x: Float, y: Float) {
        mainHandler.post {
            actionBadge?.let {
                it.text = "⌨ $text"
                it.translationX = (x + 20).coerceAtMost(550f)
                it.translationY = (y - 50).coerceAtLeast(80f)
                it.alpha = 1f

                it.animate()
                    .alpha(0f)
                    .setDuration(1200)
                    .setStartDelay(400)
                    .start()
            }
        }
    }

    fun destroy() {
        try {
            if (rootOverlay != null && rootOverlay!!.isAttachedToWindow) {
                windowManager?.removeViewImmediate(rootOverlay)
            }
        } catch (ignored: Exception) {}
        rootOverlay = null
    }
}
