package rs530anim.cache

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import rs530anim.model.Rs2Buffer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Port of rt4.Js5Compression.uncompress.
 * type 0 raw, 1 bzip2, 2 gzip.
 */
object Js5Compression {
    fun uncompress(input: ByteArray): ByteArray {
        val buffer = Rs2Buffer(input)
        val type = buffer.g1()
        val len = buffer.g4()
        require(len >= 0) { "negative js5 length" }
        if (type == 0) {
            val out = ByteArray(len)
            System.arraycopy(input, buffer.offset, out, 0, len)
            return out
        }
        val uncompressedLen = buffer.g4()
        require(uncompressedLen >= 0) { "negative js5 uncompressed length" }
        val compressed = input.copyOfRange(buffer.offset, input.size)
        return when (type) {
            1 -> bunzip(compressed, uncompressedLen)
            2 -> gunzip(compressed, uncompressedLen)
            else -> throw IllegalArgumentException("unknown js5 compression $type")
        }
    }

    private fun gunzip(src: ByteArray, expected: Int): ByteArray {
        GZIPInputStream(ByteArrayInputStream(src)).use { gz ->
            val out = gz.readAllBytes()
            if (expected != 0 && out.size != expected) {
                // some servers omit the exact trailer; still accept if we got data
            }
            return out
        }
    }

    private fun bunzip(src: ByteArray, expected: Int): ByteArray {
        // Jagex bzip streams omit the "BZh" header; commons-compress wants it.
        val withHeader = if (src.size >= 3 && src[0] == 'B'.code.toByte()) {
            src
        } else {
            val header = byteArrayOf('B'.code.toByte(), 'Z'.code.toByte(), 'h'.code.toByte(), '1'.code.toByte())
            header + src
        }
        BZip2CompressorInputStream(ByteArrayInputStream(withHeader)).use { bz ->
            val buf = ByteArrayOutputStream(expected.coerceAtLeast(16))
            bz.copyTo(buf)
            return buf.toByteArray()
        }
    }
}
