package android.widget

import android.content.Context
import app.ryzik.chat.platform.Platform

/** Всплывающая подсказка внизу окна. */
class Toast private constructor(private val text: String) {
    fun show() = Platform.toast(text)
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(@Suppress("UNUSED_PARAMETER") c: Context?, text: CharSequence, @Suppress("UNUSED_PARAMETER") d: Int) = Toast(text.toString())
    }
}
