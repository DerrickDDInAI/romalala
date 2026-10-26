package com.example.romalala

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** A compact card with readable current/nearby lyric hierarchy and touch pass-through. */
internal class OverlayWindow(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: OverlayCardView? = null
    private var previous: OverlayContent? = null
    private var params: WindowManager.LayoutParams? = null

    fun update(content: OverlayContent?) {
        if (content == null || content.sentences.isEmpty()) {
            remove()
            return
        }
        val card = view ?: OverlayCardView(context)
        if (content.sentences != previous?.sentences) card.render(content)
        val screen = bounds()
        val width = minOf(screen.width - screen.safe.left - screen.safe.right - dp(28), dp(520)).coerceAtLeast(1)
        val usableHeight = (screen.height - screen.safe.top - screen.safe.bottom).coerceAtLeast(1)
        val maxHeight = minOf(dp(280), (usableHeight * 0.5f).toInt()).coerceAtLeast(1)
        card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
        val cardHeight = card.measuredHeight.coerceAtLeast(1)
        val bottom = screen.safe.top + usableHeight * content.bottomFraction.coerceIn(0.2f, 0.98f)
        val y = (bottom.toInt() - cardHeight).coerceIn(screen.safe.top + dp(8),
            maxOf(screen.safe.top + dp(8), screen.height - screen.safe.bottom - cardHeight - dp(8)))
        val x = (screen.safe.left - screen.safe.right) / 2
        if (view == null) {
            val layout = WindowManager.LayoutParams(
                width, cardHeight, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.x = x
                this.y = y
                alpha = if (Build.VERSION.SDK_INT >= 31) {
                    minOf(0.78f, context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch)
                } else 0.78f
            }
            wm.addView(card, layout)
            params = layout
            view = card
        } else {
            val layout = params!!
            if (layout.width != width || layout.height != cardHeight || layout.y != y || layout.x != x) {
                layout.width = width
                layout.height = cardHeight
                layout.x = x
                layout.y = y
                wm.updateViewLayout(card, layout)
            }
        }
        previous = content
    }

    fun remove() {
        val old = view ?: return
        view = null
        previous = null
        params = null
        try {
            wm.removeView(old)
        } catch (_: IllegalArgumentException) {
            // Android may have detached the window after permission revocation.
        }
    }

    private data class Bounds(val width: Int, val height: Int, val safe: Rect)

    private fun bounds(): Bounds {
        if (Build.VERSION.SDK_INT >= 30) {
            val metrics = wm.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Bounds(metrics.bounds.width(), metrics.bounds.height(),
                Rect(insets.left, insets.top, insets.right, insets.bottom))
        }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return Bounds(metrics.widthPixels, metrics.heightPixels, Rect(0, dp(24), 0, dp(24)))
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
