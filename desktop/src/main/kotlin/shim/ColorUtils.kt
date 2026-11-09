package androidx.core.graphics

/** Перевод цвета ARGB ↔ HSL, как в androidx.core.graphics.ColorUtils. */
object ColorUtils {
    fun colorToHSL(color: Int, out: FloatArray) {
        val r = ((color shr 16) and 0xff) / 255f
        val g = ((color shr 8) and 0xff) / 255f
        val b = (color and 0xff) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val d = max - min
        val l = (max + min) / 2f
        var h: Float
        val s: Float
        if (max == min) { h = 0f; s = 0f } else {
            h = when (max) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }
            s = d / (1f - kotlin.math.abs(2f * l - 1f))
        }
        h = (h * 60f) % 360f
        if (h < 0) h += 360f
        out[0] = h; out[1] = s.coerceIn(0f, 1f); out[2] = l.coerceIn(0f, 1f)
    }

    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]; val s = hsl[1]; val l = hsl[2]
        val c = (1f - kotlin.math.abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - kotlin.math.abs((h / 60f % 2f) - 1f))
        val hi = (h.toInt() / 60) % 6
        val (r, g, b) = when (hi) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = Math.round(255 * (v + m)).coerceIn(0, 255)
        return (0xff shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    fun calculateLuminance(color: Int): Double {
        fun lin(c: Int): Double { val v = c / 255.0; return if (v < 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((color shr 16) and 0xff) + 0.7152 * lin((color shr 8) and 0xff) + 0.0722 * lin(color and 0xff)
    }
}
