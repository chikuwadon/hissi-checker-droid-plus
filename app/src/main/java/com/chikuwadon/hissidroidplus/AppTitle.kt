package com.chikuwadon.hissidroidplus

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView

/** Reusable branded application title; Java callers remain source-compatible. */
object AppTitle {
    @JvmStatic
    fun apply(view: TextView) {
        val title = "必死チェッカーdroid +"
        val styled = SpannableString(title)
        styled.setSpan(
            ForegroundColorSpan(Color.rgb(0, 184, 175)),
            title.indexOf("droid"),
            title.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        view.text = styled
    }
}
