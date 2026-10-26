package com.example.romalala

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.StaticLayout
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Native card shared by the floating window and the visual preview. */
internal class OverlayCardView(context: Context) : LinearLayout(context) {
    private val lavender = Color.rgb(226, 211, 238)
    private val muted = Color.rgb(217, 205, 225)

    private var headerBuddy: View? = null
    private var lyricBuddy: View? = null
    private var primaryText: TextView? = null
    private val followingRows = mutableListOf<View>()

    init {
        orientation = VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(13))
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.rgb(43, 34, 51), Color.rgb(51, 40, 61)
        )).apply {
            cornerRadius = dp(24).toFloat()
            setStroke(dp(1), Color.argb(75, 216, 202, 232))
        }
        elevation = dp(8).toFloat()
        clipToOutline = true
    }

    fun render(content: OverlayContent) {
        removeAllViews()
        followingRows.clear()
        primaryText = null
        lyricBuddy = null
        val current = content.sentences.any { it.role == SentenceRole.CURRENT }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            headerBuddy = LyricBuddyView(context, current).also { buddy ->
                buddy.visibility = GONE
                addView(buddy, LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(8) })
            }
            addView(label(context.getString(R.string.overlay_brand), 10f, lavender).apply {
                letterSpacing = 0.18f
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            if (current) addView(label(context.getString(R.string.overlay_current), 9f, lavender).apply {
                letterSpacing = 0.09f
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.rgb(78, 61, 90))
                    cornerRadius = dp(10).toFloat()
                }
            })
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9)
        })
        content.sentences.forEachIndexed { index, sentence ->
            val active = sentence.role == SentenceRole.CURRENT
            val text = label(sentence.text, if (active) 22f else if (index == 0) 17f else 15f,
                if (active) Color.rgb(246, 240, 228) else if (index == 0) Color.rgb(235, 224, 216) else muted).apply {
                typeface = Typeface.create("sans-serif-rounded", if (active) Typeface.BOLD else Typeface.NORMAL)
                setLineSpacing(dp(if (active) 3 else 2).toFloat(), 1f)
                maxLines = if (index == 0) 4 else 2
                ellipsize = TextUtils.TruncateAt.END
            }
            if (index == 0) {
                primaryText = text
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.TOP
                    isBaselineAligned = false
                    lyricBuddy = LyricBuddyView(context, active).also { buddy ->
                        addView(buddy, LayoutParams(dp(28), dp(28)).apply {
                            rightMargin = dp(8)
                        })
                    }
                    addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                }
                addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            } else {
                val following = LinearLayout(context).apply {
                    orientation = VERTICAL
                    if (index == 1) addView(View(context).apply {
                        setBackgroundColor(Color.argb(40, 216, 202, 232))
                    }, LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply {
                        topMargin = dp(8); bottomMargin = dp(6)
                    })
                    else setPadding(0, dp(3), 0, 0)
                    addView(text, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                }
                followingRows.add(following)
                addView(following, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val text = primaryText
        if (text != null) {
            val available = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
            fun lines(width: Int): Int = StaticLayout.Builder.obtain(
                text.text, 0, text.text.length, text.paint, width.coerceAtLeast(1)
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setBreakStrategy(text.breakStrategy)
                .setHyphenationFrequency(text.hyphenationFrequency).build().lineCount
            // Preserve the lyric's line count and font size; decoration yields first.
            val beside = resources.configuration.fontScale <= 1.2f &&
                available >= dp(280) && lines(available - dp(36)) <= lines(available)
            lyricBuddy?.visibility = if (beside) VISIBLE else GONE
            headerBuddy?.visibility = if (beside) GONE else VISIBLE
        }
        // Give the current sentence priority over context in short windows/large fonts.
        followingRows.forEach { it.visibility = VISIBLE }
        val naturalHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(widthMeasureSpec, naturalHeight)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val limit = MeasureSpec.getSize(heightMeasureSpec)
            for (row in followingRows.asReversed()) {
                if (measuredHeight <= limit) break
                row.visibility = GONE
                super.onMeasure(widthMeasureSpec, naturalHeight)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun label(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        includeFontPadding = false
        gravity = Gravity.START
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
