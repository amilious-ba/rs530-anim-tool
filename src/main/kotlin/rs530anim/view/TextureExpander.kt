package rs530anim.view

import javafx.scene.image.PixelWriter
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import kotlin.math.abs
import kotlin.math.floor

/**
 * First slice of Texture.method2725: 128×128 raster for the textures on
 * model 132 (112 fur, 359 tusk). Not the full TextureOp VM yet — noise +
 * the archive-26 average colour, which is what those graphs collapse toward.
 */
object TextureExpander {
    const val SIZE = 128

    fun image(textureId: Int, baseHsl: Int): WritableImage {
        val img = WritableImage(SIZE, SIZE)
        val pw = img.pixelWriter
        val base = Hsl.toFx(baseHsl)
        when (textureId) {
            359 -> fillTusk(pw, base)
            else -> fillFur(pw, base)
        }
        return img
    }

    private fun fillFur(pw: PixelWriter, base: Color) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = fbm(x / SIZE.toDouble(), y / SIZE.toDouble())
                val s = 0.55 + n * 0.55
                pw.setColor(
                    x,
                    y,
                    Color.color(
                        (base.red * s).coerceIn(0.0, 1.0),
                        (base.green * s).coerceIn(0.0, 1.0),
                        (base.blue * s).coerceIn(0.0, 1.0),
                    ),
                )
            }
        }
    }

    private fun fillTusk(pw: PixelWriter, base: Color) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                val n = 0.85 + 0.15 * fbm(x / 32.0, y / 32.0)
                pw.setColor(
                    x,
                    y,
                    Color.color(
                        (base.red * n + 0.15).coerceIn(0.0, 1.0),
                        (base.green * n + 0.12).coerceIn(0.0, 1.0),
                        (base.blue * n + 0.10).coerceIn(0.0, 1.0),
                    ),
                )
            }
        }
    }

    private fun fbm(x: Double, y: Double): Double {
        var f = 0.0
        var amp = 0.5
        var fx = x * 6.0
        var fy = y * 6.0
        repeat(4) {
            f += amp * noise(fx, fy)
            fx *= 2.03
            fy *= 2.03
            amp *= 0.5
        }
        return f
    }

    private fun noise(x: Double, y: Double): Double {
        val x0 = floor(x)
        val y0 = floor(y)
        val tx = x - x0
        val ty = y - y0
        val v00 = hash(x0, y0)
        val v10 = hash(x0 + 1, y0)
        val v01 = hash(x0, y0 + 1)
        val v11 = hash(x0 + 1, y0 + 1)
        val sx = tx * tx * (3 - 2 * tx)
        val sy = ty * ty * (3 - 2 * ty)
        val a = v00 + (v10 - v00) * sx
        val b = v01 + (v11 - v01) * sx
        return a + (b - a) * sy
    }

    private fun hash(x: Double, y: Double): Double {
        var n = (x * 127.1 + y * 311.7)
        n = abs(kotlin.math.sin(n) * 43758.5453)
        return (n - floor(n)) * 2.0 - 1.0
    }
}
