package com.example.romalala

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** A single, scrollable setup flow; the illustration doubles as the replay control. */
internal class RomalalaMenuView(context: Context) : ScrollView(context) {
    private val column = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = minOf(MeasureSpec.getSize(widthMeasureSpec), dp(480))
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
        }
    }.apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
    }
    val conversion = ConversionDemoView(context)
    val primary = RomalalaStyle.button(context, context.getString(R.string.start_singing), true)
    val spotify = RomalalaStyle.button(context, context.getString(R.string.open_spotify))
    private val guidance = RomalalaStyle.text(context, "", 13f, RomalalaStyle.muted).apply {
        gravity = Gravity.CENTER
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val helpToggle = RomalalaStyle.button(context, context.getString(R.string.how_it_works)).apply {
        background = RomalalaStyle.rounded(context, 0xFFFFFCF5.toInt(), 18)
    }
    private val help = RomalalaStyle.text(context, context.getString(R.string.setup_instructions), 14f).apply {
        visibility = GONE
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = RomalalaStyle.rounded(context, 0xFFFFFCF5.toInt(), 18)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    var onPrimary: (() -> Unit)? = null
    var onSpotify: (() -> Unit)? = null

    init {
        id = View.generateViewId()
        isFillViewport = true
        setBackgroundColor(RomalalaStyle.cream)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        fun add(view: View, top: Int = 0) {
            column.addView(view, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(top)
            })
        }
        add(RomalalaStyle.text(context, context.getString(R.string.app_name), 20f, bold = true).apply {
            gravity = Gravity.CENTER
        })
        add(RomalalaStyle.text(context, context.getString(R.string.menu_title), 32f, bold = true).apply {
            gravity = Gravity.CENTER
            setLineSpacing(dp(1).toFloat(), 1f)
            isAccessibilityHeading = true
        }, 32)
        add(conversion, 16)
        add(RomalalaStyle.text(context, context.getString(R.string.demo_direction), 13f, RomalalaStyle.muted).apply {
            gravity = Gravity.CENTER
        }, 8)
        add(primary, 26)
        primary.setOnClickListener { onPrimary?.invoke() }
        add(guidance, 10)
        add(spotify, 6)
        spotify.setOnClickListener { onSpotify?.invoke() }
        add(helpToggle, 12)
        helpToggle.setOnClickListener {
            help.visibility = if (help.visibility == VISIBLE) GONE else VISIBLE
            helpToggle.setText(if (help.visibility == VISIBLE) R.string.close_help else R.string.how_it_works)
        }
        add(help, 8)
        update(false, false, false)
    }

    fun update(overlayAllowed: Boolean, running: Boolean, busy: Boolean, stopping: Boolean = false) {
        primary.setText(when {
            stopping -> R.string.stopping
            busy -> R.string.waiting_permission
            running -> R.string.stop_overlay
            !overlayAllowed -> R.string.allow_overlay
            else -> R.string.start_singing
        })
        primary.isEnabled = !busy && !stopping
        primary.alpha = if (primary.isEnabled) 1f else .6f
        guidance.visibility = if (!overlayAllowed || running) VISIBLE else GONE
        guidance.setText(if (running) R.string.active_guidance else R.string.permission_guidance)
        spotify.visibility = if (running) VISIBLE else GONE
    }
    fun stopAnimation() = conversion.finish()
    private fun dp(value: Int) = RomalalaStyle.dp(context, value)
}
