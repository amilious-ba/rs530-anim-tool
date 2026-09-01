package rs530anim.model

/**
 * Subset of rt4.Buffer used by this tool.
 * Source of truth: vendor/rt4/Buffer.java (pasted from the running client).
 *
 * Names and math match the client:
 *   g1      unsigned byte
 *   g1b     signed byte
 *   g2      unsigned big-endian short
 *   g2b     signed big-endian short
 *   gsmart  signed smart   (byte-64, or ushort-0xC000)   range -16384..16383
 *   gsmarts unsigned smart (byte, or ushort-0x8000)
 */
class Rs2Buffer(val data: ByteArray, var offset: Int = 0) {
    constructor(capacity: Int) : this(ByteArray(capacity), 0)

    fun writtenBytes(): ByteArray = data.copyOf(offset)

    fun remaining(): Int = data.size - offset

    fun g1(): Int = data[offset++].toInt() and 0xFF

    fun g1b(): Byte = data[offset++]

    fun g2(): Int {
        offset += 2
        return ((data[offset - 2].toInt() and 0xFF) shl 8) + (data[offset - 1].toInt() and 0xFF)
    }

    fun g3(): Int {
        offset += 3
        return ((data[offset - 3].toInt() and 0xFF) shl 16) +
            ((data[offset - 2].toInt() and 0xFF) shl 8) +
            (data[offset - 1].toInt() and 0xFF)
    }

    fun g4(): Int {
        offset += 4
        return ((data[offset - 4].toInt() and 0xFF) shl 24) +
            ((data[offset - 3].toInt() and 0xFF) shl 16) +
            ((data[offset - 2].toInt() and 0xFF) shl 8) +
            (data[offset - 1].toInt() and 0xFF)
    }

    fun g2b(): Int {
        offset += 2
        var value = ((data[offset - 2].toInt() and 0xFF) shl 8) + (data[offset - 1].toInt() and 0xFF)
        if (value > 32767) value -= 0x10000
        return value
    }

    fun gsmart(): Int {
        val peek = data[offset].toInt() and 0xFF
        return if (peek < 128) g1() - 64 else g2() - 0xC000
    }

    fun gsmarts(): Int {
        val peek = data[offset].toInt() and 0xFF
        return if (peek >= 128) g2() - 0x8000 else g1()
    }

    fun p1(value: Int) {
        data[offset++] = value.toByte()
    }

    fun p2(value: Int) {
        data[offset++] = (value shr 8).toByte()
        data[offset++] = value.toByte()
    }

    /** Unsigned smart write. Matches rt4.Buffer.psmarts. */
    fun psmarts(value: Int) {
        when {
            value in 0 until 128 -> p1(value)
            value in 0 until 0x8000 -> p2(value + 0x8000)
            else -> throw IllegalArgumentException("psmarts out of range: $value")
        }
    }

    /**
     * Signed smart write. Inverse of gsmart. Buffer.java has no psmart;
     * this is the unique inverse of the client's gsmart.
     */
    fun psmart(value: Int) {
        when {
            value >= -64 && value < 128 -> p1(value + 64)
            value >= -16384 && value < 16384 -> p2(value + 0xC000)
            else -> throw IllegalArgumentException("psmart out of range: $value")
        }
    }
}
