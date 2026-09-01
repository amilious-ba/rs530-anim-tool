package rs530anim.view

import javafx.scene.paint.Color
import kotlin.math.pow

/**
 * Rasteriser.calculateBrightness + ColorUtils.multiplyLightness2.
 * Palette index is 16-bit HSL (hue 6, sat 3, light 7).
 */
object Hsl {
    private const val BRIGHTNESS = 0.8
    private val palette = IntArray(65536)

    init {
        var offset = 0
        for (y in 0 until 512) {
            val hue = (y shr 3) / 64.0 + 0.0078125
            val saturation = (y and 7) / 8.0 + 0.0625
            for (x in 0 until 128) {
                val lightness = x / 128.0
                var r = lightness
                var g = lightness
                var b = lightness
                if (saturation != 0.0) {
                    val q = if (lightness < 0.5) {
                        lightness * (saturation + 1.0)
                    } else {
                        lightness + saturation - lightness * saturation
                    }
                    val p = lightness * 2.0 - q
                    var t = hue + 1.0 / 3.0
                    if (t > 1.0) t -= 1.0
                    var d11 = hue - 1.0 / 3.0
                    if (d11 < 0.0) d11 += 1.0
                    r = channel(p, q, t)
                    g = channel(p, q, hue)
                    b = channel(p, q, d11)
                }
                r = r.pow(BRIGHTNESS)
                g = g.pow(BRIGHTNESS)
                b = b.pow(BRIGHTNESS)
                var rgb = ((r * 256.0).toInt() shl 16) +
                    ((g * 256.0).toInt() shl 8) +
                    (b * 256.0).toInt()
                if (rgb == 0) rgb = 1
                palette[offset++] = rgb
            }
        }
    }

    fun toFx(hsl: Int): Color {
        val rgb = palette[hsl and 0xFFFF]
        return Color.rgb((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }

    fun multiplyLightness(hsl: Int, lightness: Int): Int {
        var b = lightness * (hsl and 0x7F) shr 7
        if (b < 2) b = 2
        if (b > 126) b = 126
        return (hsl and 0xFF80) + b
    }

    private fun channel(p: Double, q: Double, t: Double): Double = when {
        t * 6.0 < 1.0 -> p + (q - p) * 6.0 * t
        t * 2.0 < 1.0 -> q
        t * 3.0 < 2.0 -> p + (q - p) * (2.0 / 3.0 - t) * 6.0
        else -> p
    }
}
