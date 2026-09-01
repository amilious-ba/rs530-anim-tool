package rs530anim.view

import javafx.scene.paint.Color

/**
 * 530 face colour is 16-bit HSL: hue 6, sat 3, light 7.
 * Converted the same way the software renderer builds its palette.
 */
object Hsl {
    fun toFx(hsl: Int): Color {
        val rgb = toRgb(hsl and 0xFFFF)
        return Color.rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }

    fun toRgb(hsl: Int): Int {
        val hue = hsl shr 10 and 0x3F
        val sat = hsl shr 7 and 0x7
        val lum = hsl and 0x7F
        if (sat == 0) {
            val v = (lum * 2).coerceIn(0, 255)
            return v shl 16 or (v shl 8) or v
        }
        val l = lum / 128.0
        val s = (sat + 1) / 8.0
        val h = hue / 64.0
        val q = if (l < 0.5) l * (1.0 + s) else l + s - l * s
        val p = 2.0 * l - q
        val r = hueToRgb(p, q, h + 1.0 / 3.0)
        val g = hueToRgb(p, q, h)
        val b = hueToRgb(p, q, h - 1.0 / 3.0)
        return (r * 255).toInt().coerceIn(0, 255) shl 16 or
            ((g * 255).toInt().coerceIn(0, 255) shl 8) or
            (b * 255).toInt().coerceIn(0, 255)
    }

    private fun hueToRgb(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0) t += 1.0
        if (t > 1) t -= 1.0
        return when {
            t < 1.0 / 6.0 -> p + (q - p) * 6.0 * t
            t < 0.5 -> q
            t < 2.0 / 3.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
            else -> p
        }
    }
}
