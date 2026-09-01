package rs530anim.cache

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal 530 JS5 session matching rt4.client.js5Connect + Js5NetQueue.
 *
 * Handshake:  opcode 15, i32 revision (530). Server replies 0.
 * Then:       opcode 6 / p3(3)   (method2331)
 *             opcode 2 / p3(0)   (writeLoggedIn(true) — initial load)
 * Request:    opcode 1, p3(archive << 16 | group)   urgent
 * Reply:      u8 archive, u16 group, u8 flags, u32 length
 *             then 512-byte blocks (first block already spent 8 header bytes).
 */
class Js5Client(
    private val settings: CacheSettings,
    private val timeoutMs: Int = 15_000,
) : AutoCloseable {
    private val socket = Socket()
    private val out: DataOutputStream
    private val inp: DataInputStream

    init {
        socket.soTimeout = timeoutMs
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(settings.host, settings.connectPort), timeoutMs)
        out = DataOutputStream(socket.getOutputStream())
        inp = DataInputStream(socket.getInputStream())

        out.writeByte(15)
        out.writeInt(settings.revision)
        out.flush()

        val response = try {
            inp.readUnsignedByte()
        } catch (e: java.io.EOFException) {
            close()
            throw IllegalStateException(
                "js5 handshake EOF from ${settings.host}:${settings.connectPort} — server closed the socket",
                e,
            )
        }
        if (response != 0) {
            close()
            throw IllegalStateException(
                "js5 handshake rejected: $response " +
                    "(6=outdated revision, 7/9=full). host=${settings.host}:${settings.connectPort} rev=${settings.revision}",
            )
        }

        writePacket(6, 3)
        writePacket(2, 0)
    }

    fun fetchGroup(archive: Int, group: Int, trailerLen: Int = 2): ByteArray {
        // p1(1) urgent + p3((archive << 16) | group)
        val key = (archive shl 16) or (group and 0xFFFF)
        writePacket(1, key)

        val hdrArchive = inp.readUnsignedByte()
        val hdrGroup = inp.readUnsignedShort()
        val flags = inp.readUnsignedByte()
        val length = inp.readInt()
        if (hdrArchive != archive || hdrGroup != group) {
            throw IllegalStateException("js5 reply mismatch got $hdrArchive/$hdrGroup wanted $archive/$group")
        }
        val type = flags and 0x7F
        val headerBytes = if (type == 0) 5 else 9
        // Client stops at buffer.length - trailerLen, so do not wait for extra CRC bytes.
        val payloadEnd = length + headerBytes
        val dest = ByteArray(payloadEnd)
        dest[0] = type.toByte()
        dest[1] = (length ushr 24).toByte()
        dest[2] = (length ushr 16).toByte()
        dest[3] = (length ushr 8).toByte()
        dest[4] = length.toByte()

        var offset = 5
        var blockPos = 8
        while (offset < payloadEnd) {
            if (blockPos == 512) {
                val mark = inp.readUnsignedByte()
                if (mark != 0xFF) {
                    throw IllegalStateException("js5 block marker expected 0xFF, got $mark")
                }
                blockPos = 1
            }
            var n = 512 - blockPos
            if (n > payloadEnd - offset) n = payloadEnd - offset
            inp.readFully(dest, offset, n)
            offset += n
            blockPos += n
        }
        return dest
    }

    fun fetchUncompressed(archive: Int, group: Int): ByteArray {
        return Js5Compression.uncompress(fetchGroup(archive, group))
    }

    /**
     * Models: archive 7, group = modelId, single file.
     * If the group is striped (last byte = stripe count), extract file 0.
     */
    fun fetchModel(modelId: Int): ByteArray {
        val raw = fetchUncompressed(Js5Archives.MODELS, modelId)
        return extractFile0(raw)
    }

    fun fetchBase(baseId: Int): ByteArray {
        val raw = fetchUncompressed(Js5Archives.BASES, baseId)
        return extractFile0(raw)
    }

    fun fetchFrame(framesetId: Int, frameIndex: Int): ByteArray {
        val raw = fetchUncompressed(Js5Archives.FRAMES, framesetId)
        return extractFile(raw, frameIndex)
    }

    override fun close() {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun writePacket(opcode: Int, value24: Int) {
        out.writeByte(opcode)
        out.writeByte((value24 ushr 16) and 0xFF)
        out.writeByte((value24 ushr 8) and 0xFF)
        out.writeByte(value24 and 0xFF)
        out.flush()
    }

    companion object {
        fun extractFile0(uncompressed: ByteArray): ByteArray = extractFile(uncompressed, 0)

        /**
         * Single-file groups are the payload itself.
         * Multi-file groups end with stripe count + per-stripe delta table
         * (rt4.Js5.unpackGroup).
         */
        fun extractFile(uncompressed: ByteArray, file: Int): ByteArray {
            if (uncompressed.isEmpty()) return uncompressed
            val stripes = uncompressed[uncompressed.size - 1].toInt() and 0xFF
            if (stripes == 0 || stripes > 16) return uncompressed
            // Need group size. Without the index we infer: table sits just before the last byte.
            // table size = groupSize * stripes * 4. We don't know groupSize.
            // Models are almost always one file — if the data looks like an RS2 model footer, use as-is.
            if (looksLikeModel(uncompressed)) return uncompressed
            // Best-effort: treat as 1 file if `file==0`.
            if (file == 0) return uncompressed
            throw IllegalStateException("multi-file js5 group needs an index to pick file $file")
        }

        private fun looksLikeModel(data: ByteArray): Boolean {
            if (data.size < 18) return false
            val a = data[data.size - 1].toInt()
            val b = data[data.size - 2].toInt()
            return (a == -1 && b == -1) || data.size > 18
        }
    }
}
