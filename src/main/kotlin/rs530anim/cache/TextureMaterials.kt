package rs530anim.cache

import rs530anim.model.Rs2Buffer

/**
 * Archive 26 group 0 file 0 — same table Js5GlTextureProvider reads.
 * aShortArray59[id] is the solid HSL used when the procedural texture
 * is not expanded (GlSolidColorTexture).
 */
class TextureMaterials(private val averageHsl: ShortArray) {
    fun solidHsl(textureId: Int): Int? {
        if (textureId < 0 || textureId >= averageHsl.size) return null
        val hsl = averageHsl[textureId].toInt() and 0xFFFF
        return if (hsl == 0) null else hsl
    }

    companion object {
        fun load(store: Js5Store): TextureMaterials {
            val files = store.groupFiles(Js5Archives.TEXTURE_META, 0)
            val bytes = files[0] ?: files.values.first()
            return decode(bytes)
        }

        fun decode(bytes: ByteArray): TextureMaterials {
            val b = Rs2Buffer(bytes)
            val count = b.g2()
            val present = BooleanArray(count)
            for (i in 0 until count) present[i] = b.g1() == 1
            repeat(4) {
                for (i in 0 until count) if (present[i]) b.g1()
            }
            repeat(4) {
                for (i in 0 until count) if (present[i]) b.g1()
            }
            val hsl = ShortArray(count)
            for (i in 0 until count) {
                if (present[i]) hsl[i] = b.g2().toShort()
            }
            return TextureMaterials(hsl)
        }
    }
}
