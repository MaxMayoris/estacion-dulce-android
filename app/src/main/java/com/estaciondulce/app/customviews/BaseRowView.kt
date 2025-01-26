package com.estaciondulce.app.customviews

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.estaciondulce.app.R

class BaseRowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    init {
        orientation = HORIZONTAL
    }

    fun setRowColor(isEvenRow: Boolean) {
        val backgroundColor = if (isEvenRow) {
            ContextCompat.getColor(context, R.color.light_purple)
        } else {
            ContextCompat.getColor(context, android.R.color.white)
        }
        setBackgroundColor(backgroundColor)
    }

    fun setAttributes(attributes: List<String>) {
        removeAllViews()
        for (attribute in attributes) {
            val textView = TextView(context).apply {
                text = attribute
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setPadding(8, 8, 8, 8)
                textAlignment = TEXT_ALIGNMENT_CENTER
            }
            addView(textView)
        }
    }
}
