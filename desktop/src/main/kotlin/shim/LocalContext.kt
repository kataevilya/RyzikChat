package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/** Как LocalContext на Android: экраны берут отсюда папки и «открыть ссылку». */
val LocalContext = staticCompositionLocalOf { Context.instance }
