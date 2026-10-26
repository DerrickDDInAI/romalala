package com.example.romalala

import android.content.Context
import android.widget.FrameLayout
import android.widget.ImageView

/** The approved Lumi artwork, shared with the menu and launcher. */
internal class LyricBuddyView @JvmOverloads constructor(context: Context, singing: Boolean = false) : FrameLayout(context) {
    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(ImageView(context).apply {
            setImageResource(R.drawable.lumi_singing)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = if (singing) 1f else .85f
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
}
