package com.example.romalala

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.TextView

internal object RomalalaStyle {
    val cream = Color.rgb(255, 249, 237)
    val ink = Color.rgb(42, 25, 62)
    val muted = Color.rgb(113, 99, 118)
    val lavender = Color.rgb(204, 183, 249)
    val well = Color.rgb(243, 235, 249)
    val sage = Color.rgb(225, 234, 204)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun rounded(context: Context, color: Int, radius: Int = 22) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
    }
    fun text(context: Context, value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        typeface = Typeface.create(if (bold) "sans-serif-rounded" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }
    fun button(context: Context, value: String, primary: Boolean = false) = Button(context).apply {
        text = value; textSize = if (primary) 18f else 14f; isAllCaps = false
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        setTextColor(ink)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(40, 86, 62, 108)),
            rounded(context, if (primary) lavender else cream, 18), null)
        minHeight = dp(context, if (primary) 56 else 48)
        minimumHeight = minHeight; minWidth = 0; minimumWidth = 0
        gravity = Gravity.CENTER
        setPadding(dp(context, 18), dp(context, 12), dp(context, 18), dp(context, 12))
        stateListAnimator = null
    }
}
