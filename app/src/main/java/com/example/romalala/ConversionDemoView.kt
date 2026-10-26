package com.example.romalala

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin

/** A tappable, one-shot pronunciation example, never a live OCR/audio indicator. */
internal class ConversionDemoView(context: Context) : LinearLayout(context) {
    private val words = mutableListOf<TextView>()
    private val staff = Staff(context)
    private val hero = ImageView(context).apply {
        setImageResource(R.drawable.lumi_singing)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val phrase = LinearLayout(context).apply { orientation = VERTICAL }
    private var animator: ValueAnimator? = null
    private val stacked = resources.configuration.fontScale >= 1.3f

    init {
        gravity = Gravity.CENTER
        orientation = if (stacked) VERTICAL else HORIZONTAL
        isFocusable = true
        contentDescription = context.getString(R.string.demo_accessibility)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { play() }
        addView(hero, if (stacked) LayoutParams(LayoutParams.MATCH_PARENT, dp(180))
            else LayoutParams(0, dp(224), .46f))
        phrase.addView(staff, LayoutParams(LayoutParams.MATCH_PARENT, dp(60)))
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(15), dp(10), dp(15))
            background = RomalalaStyle.rounded(context, 0xFFFFFCF5.toInt(), 18)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        listOf("世界" to "sekai", "の" to "no", "歌" to "uta").forEach { (original, romanized) ->
            row.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                addView(RomalalaStyle.text(context, original, 20f, bold = true))
                addView(RomalalaStyle.text(context, romanized, 17f).also { words.add(it) },
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, if (romanized == "sekai") 1.5f else 1f))
        }
        phrase.addView(row)
        addView(phrase, if (stacked) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            else LayoutParams(0, LayoutParams.WRAP_CONTENT, .54f))
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = android.widget.Button::class.java.name
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(
            AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.demo_replay)))
    }

    fun play() {
        finish()
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        if (!ValueAnimator.areAnimatorsEnabled() || accessibility.isTouchExplorationEnabled) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2100
            addUpdateListener { renderProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { resetVisuals() }
            })
            start()
        }
    }
    internal fun renderProgress(progress: Float) {
        staff.progress = progress
        words.forEachIndexed { index, word ->
            val reveal = ((progress - .15f - index * .23f) / .17f).coerceIn(0f, 1f)
            word.alpha = reveal
            word.translationY = dp(7) * (1f - reveal)
        }
        hero.translationY = (-sin(progress * Math.PI) * dp(3)).toFloat()
    }
    fun finish() { animator?.cancel(); animator = null; resetVisuals() }
    private fun resetVisuals() {
        words.forEach { it.alpha = 1f; it.translationY = 0f }
        staff.progress = 1f
        hero.translationY = 0f
    }
    override fun onDetachedFromWindow() { finish(); super.onDetachedFromWindow() }
    private fun dp(value: Int) = RomalalaStyle.dp(context, value)

    private class Staff(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val curve = Path()
        var progress = 1f
            set(value) { field = value; invalidate() }
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            paint.color = 0xFFC0A9DA.toInt()
            paint.strokeWidth = resources.displayMetrics.density
            paint.style = Paint.Style.STROKE
            val gap = height / 10f
            for (line in 0..4) {
                val y = height * .52f + line * gap
                curve.reset(); curve.moveTo(0f, y)
                curve.cubicTo(width * .35f, y - gap * 3, width * .7f, y - gap * 3, width.toFloat(), y - gap * 2)
                canvas.drawPath(curve, paint)
            }
            paint.style = Paint.Style.FILL
            paint.textSize = height * .52f
            paint.color = 0xFFB59AD9.toInt()
            canvas.drawText("♪", width * .7f, height * .48f, paint)
            paint.color = 0xFFE8BD55.toInt()
            canvas.drawText("♪", width * .22f, height * .55f, paint)
            if (progress < 1f) {
                paint.color = 0xFF70578F.toInt()
                canvas.drawText("♪", (width - paint.measureText("♪")) * progress, height * .65f, paint)
            }
        }
    }
}
