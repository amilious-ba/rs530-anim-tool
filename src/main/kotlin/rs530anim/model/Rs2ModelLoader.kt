package rs530anim.model

import java.nio.file.Files
import java.nio.file.Path

/**
 * Port of rt4.RawModel.<init>(byte[]), decodeOld, decodeNew from
 * vendor/rt4/RawModel.java (AmiliousScape-Client).
 *
 * Only geometry + bone labels are kept. Texture PMN / particle blocks are
 * skipped after their offsets are reserved, same as the client walk.
 */
object Rs2ModelLoader {
    fun load(path: Path): Rs2Model {
        val bytes = Files.readAllBytes(path)
        if (bytes.size < 18) {
            throw IllegalArgumentException("File too small to be an RS2 model: ${bytes.size} bytes")
        }
        return decode(bytes)
    }

    fun decode(src: ByteArray): Rs2Model {
        return if (src[src.size - 1].toInt() == -1 && src[src.size - 2].toInt() == -1) {
            decodeNew(src)
        } else {
            decodeOld(src)
        }
    }

    private fun decodeNew(src: ByteArray): Rs2Model {
        val buffer1 = Rs2Buffer(src)
        buffer1.offset = src.size - 23

        val vertexCount = buffer1.g2()
        val triangleCount = buffer1.g2()
        val texturedCount = buffer1.g1()
        val hasInfo = buffer1.g1()
        val hasTriangleInfo = hasInfo and 0x1 == 1
        val priority = buffer1.g1()
        val hasAlpha = buffer1.g1()
        val hasTriangleBones = buffer1.g1()
        val hasTextures = buffer1.g1()
        val hasVertexBones = buffer1.g1()
        val dxDataLength = buffer1.g2()
        val dyDataLength = buffer1.g2()
        val dzDataLength = buffer1.g2()
        val vertexIndexDataLength = buffer1.g2()
        val triangleTextureDataLength = buffer1.g2()

        var simpleTextureFaceCount = 0
        var complexTextureFaceCount = 0
        var cubeTextureFaceCount = 0
        if (texturedCount > 0) {
            buffer1.offset = 0
            for (i in 0 until texturedCount) {
                val type = buffer1.g1b().toInt()
                when {
                    type == 0 -> simpleTextureFaceCount++
                    type in 1..3 -> complexTextureFaceCount++
                }
                if (type == 2) cubeTextureFaceCount++
            }
        }

        var offset = texturedCount
        offset += vertexCount
        if (hasTriangleInfo) offset += triangleCount
        val triangleTypeDataOffset = offset
        offset += triangleCount
        if (priority == 255) offset += triangleCount
        val triangleBonesDataOffset = offset
        if (hasTriangleBones == 1) offset += triangleCount
        val vertexBonesDataOffset = offset
        if (hasVertexBones == 1) offset += vertexCount
        val alphaDataOffset = offset
        if (hasAlpha == 1) offset += triangleCount
        val vertexIndexDataOffset = offset
        offset += vertexIndexDataLength
        val triangleTexturesDataOffset = offset
        if (hasTextures == 1) offset += triangleCount * 2
        offset += triangleTextureDataLength
        val triangleColorDataOffset = offset
        offset += triangleCount * 2
        val dxDataOffset = offset
        offset += dxDataLength
        val dyDataOffset = offset
        offset += dyDataLength
        val dzDataOffset = offset
        offset += dzDataLength
        offset += simpleTextureFaceCount * 6
        offset += complexTextureFaceCount * 6
        offset += complexTextureFaceCount * 6
        offset += complexTextureFaceCount
        offset += complexTextureFaceCount
        offset += complexTextureFaceCount + cubeTextureFaceCount * 2

        require(offset <= src.size) {
            "RawModel.decodeNew offsets past file ($offset > ${src.size})"
        }

        val flagsBuf = Rs2Buffer(src, texturedCount)
        val xBuf = Rs2Buffer(src, dxDataOffset)
        val yBuf = Rs2Buffer(src, dyDataOffset)
        val zBuf = Rs2Buffer(src, dzDataOffset)
        val boneBuf = if (hasVertexBones == 1) Rs2Buffer(src, vertexBonesDataOffset) else null

        val vx = IntArray(vertexCount)
        val vy = IntArray(vertexCount)
        val vz = IntArray(vertexCount)
        val vertexBones = if (hasVertexBones == 1) IntArray(vertexCount) else null
        var prevX = 0
        var prevY = 0
        var prevZ = 0
        for (v in 0 until vertexCount) {
            val flags = flagsBuf.g1()
            var dx = 0
            var dy = 0
            var dz = 0
            if (flags and 0x1 != 0) dx = xBuf.gsmart()
            if (flags and 0x2 != 0) dy = yBuf.gsmart()
            if (flags and 0x4 != 0) dz = zBuf.gsmart()
            prevX += dx
            prevY += dy
            prevZ += dz
            vx[v] = prevX
            vy[v] = prevY
            vz[v] = prevZ
            if (vertexBones != null) vertexBones[v] = boneBuf!!.g1()
        }

        val colorBuf = Rs2Buffer(src, triangleColorDataOffset)
        val faceColors = ShortArray(triangleCount) { colorBuf.g2().toShort() }
        val faceTextures = if (hasTextures == 1) {
            val texBuf = Rs2Buffer(src, triangleTexturesDataOffset)
            ShortArray(triangleCount) { (texBuf.g2() - 1).toShort() }
        } else {
            null
        }

        val typeBuf = Rs2Buffer(src, triangleTypeDataOffset)
        val indexBuf = Rs2Buffer(src, vertexIndexDataOffset)
        val fa = IntArray(triangleCount)
        val fb = IntArray(triangleCount)
        val fc = IntArray(triangleCount)
        readFaces(typeBuf, indexBuf, triangleCount, fa, fb, fc)

        val triangleBones = if (hasTriangleBones == 1) {
            val buf = Rs2Buffer(src, triangleBonesDataOffset)
            IntArray(triangleCount) { buf.g1() }
        } else {
            null
        }
        val faceAlpha = if (hasAlpha == 1) {
            src.copyOfRange(alphaDataOffset, alphaDataOffset + triangleCount)
        } else {
            null
        }

        val textureTypes = if (texturedCount > 0) ByteArray(texturedCount) { src[it] } else null
        val textureP = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureM = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureN = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureScaleX = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureScaleY = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureScaleZ = if (texturedCount > 0) ShortArray(texturedCount) else null
        val textureRotY = if (texturedCount > 0) ByteArray(texturedCount) else null
        val textureDir = if (texturedCount > 0) ByteArray(texturedCount) else null
        val textureOff = if (texturedCount > 0) ByteArray(texturedCount) else null
        if (texturedCount > 0) {
            val simpleOff = dzDataOffset + dzDataLength
            val simpleBuf = Rs2Buffer(src, simpleOff)
            val complexOff = simpleOff + simpleTextureFaceCount * 6
            val complexBuf = Rs2Buffer(src, complexOff)
            val scaleBuf = Rs2Buffer(src, complexOff + complexTextureFaceCount * 6)
            val rotBuf = Rs2Buffer(src, complexOff + complexTextureFaceCount * 12)
            val dirBuf = Rs2Buffer(src, rotBuf.offset + complexTextureFaceCount)
            // rotBuf offset after construct is start; compute explicitly
            val rotOff = complexOff + complexTextureFaceCount * 12
            val dirOff = rotOff + complexTextureFaceCount
            val offOff = dirOff + complexTextureFaceCount
            val rotB = Rs2Buffer(src, rotOff)
            val dirB = Rs2Buffer(src, dirOff)
            val offB = Rs2Buffer(src, offOff)
            for (t in 0 until texturedCount) {
                val typ = textureTypes!![t].toInt() and 0xFF
                if (typ == 0) {
                    textureP!![t] = simpleBuf.g2().toShort()
                    textureM!![t] = simpleBuf.g2().toShort()
                    textureN!![t] = simpleBuf.g2().toShort()
                } else {
                    textureP!![t] = complexBuf.g2().toShort()
                    textureM!![t] = complexBuf.g2().toShort()
                    textureN!![t] = complexBuf.g2().toShort()
                    textureScaleX!![t] = scaleBuf.g2().toShort()
                    textureScaleY!![t] = scaleBuf.g2().toShort()
                    textureScaleZ!![t] = scaleBuf.g2().toShort()
                    textureRotY!![t] = rotB.g1b()
                    textureDir!![t] = dirB.g1b()
                    textureOff!![t] = offB.g1b()
                }
            }
        }
        val textureIndex = if (hasTextures == 1 && texturedCount > 0) {
            val buf = Rs2Buffer(src, triangleTexturesDataOffset + triangleCount * 2)
            ByteArray(triangleCount) { i ->
                if (faceTextures!![i].toInt() == -1) -1 else (buf.g1() - 1).toByte()
            }
        } else {
            null
        }

        return Rs2Model(
            format = "new",
            vertexCount = vertexCount,
            faceCount = triangleCount,
            verticesX = vx,
            verticesY = vy,
            verticesZ = vz,
            faceA = fa,
            faceB = fb,
            faceC = fc,
            faceColors = faceColors,
            faceTextures = faceTextures,
            vertexBones = vertexBones,
            triangleBones = triangleBones,
            faceAlpha = faceAlpha,
            textureTypes = textureTypes,
            textureP = textureP,
            textureM = textureM,
            textureN = textureN,
            textureIndex = textureIndex,
            textureScaleX = textureScaleX,
            textureScaleY = textureScaleY,
            textureScaleZ = textureScaleZ,
            textureRotY = textureRotY,
            textureDir = textureDir,
            textureOff = textureOff,
        )
    }

    private fun decodeOld(src: ByteArray): Rs2Model {
        val footer = Rs2Buffer(src, src.size - 18)
        val vertexCount = footer.g2()
        val triangleCount = footer.g2()
        val texturedCount = footer.g1()
        val hasInfo = footer.g1()
        val hasPriorities = footer.g1()
        val hasAlpha = footer.g1()
        val hasTriangleBones = footer.g1()
        val hasVertexBones = footer.g1()
        val dxDataLength = footer.g2()
        val dyDataLength = footer.g2()
        footer.g2()
        val vertexIndexDataLength = footer.g2()

        var offset = vertexCount
        val triangleTypeDataOffset = offset
        offset += triangleCount
        if (hasPriorities == 255) offset += triangleCount
        val triangleBonesDataOffset = offset
        if (hasTriangleBones == 1) offset += triangleCount
        if (hasInfo == 1) offset += triangleCount
        val vertexBonesOffset = offset
        if (hasVertexBones == 1) offset += vertexCount
        val alphaDataOffset = offset
        if (hasAlpha == 1) offset += triangleCount
        val vertexIndexDataOffset = offset
        offset += vertexIndexDataLength
        val triangleColorDataOffset = offset
        offset += triangleCount * 2
        offset += texturedCount * 6
        val dxDataOffset = offset
        offset += dxDataLength
        val dyDataOffset = offset
        offset += dyDataLength
        val dzDataOffset = offset

        val flagsBuf = Rs2Buffer(src, 0)
        val xBuf = Rs2Buffer(src, dxDataOffset)
        val yBuf = Rs2Buffer(src, dyDataOffset)
        val zBuf = Rs2Buffer(src, dzDataOffset)
        val boneBuf = if (hasVertexBones == 1) Rs2Buffer(src, vertexBonesOffset) else null

        val vx = IntArray(vertexCount)
        val vy = IntArray(vertexCount)
        val vz = IntArray(vertexCount)
        val vertexBones = if (hasVertexBones == 1) IntArray(vertexCount) else null
        var prevX = 0
        var prevY = 0
        var prevZ = 0
        for (v in 0 until vertexCount) {
            val flags = flagsBuf.g1()
            var dx = 0
            var dy = 0
            var dz = 0
            if (flags and 0x1 != 0) dx = xBuf.gsmart()
            if (flags and 0x2 != 0) dy = yBuf.gsmart()
            if (flags and 0x4 != 0) dz = zBuf.gsmart()
            prevX += dx
            prevY += dy
            prevZ += dz
            vx[v] = prevX
            vy[v] = prevY
            vz[v] = prevZ
            if (vertexBones != null) vertexBones[v] = boneBuf!!.g1()
        }

        val colorBuf = Rs2Buffer(src, triangleColorDataOffset)
        val faceColors = ShortArray(triangleCount) { colorBuf.g2().toShort() }

        val typeBuf = Rs2Buffer(src, triangleTypeDataOffset)
        val indexBuf = Rs2Buffer(src, vertexIndexDataOffset)
        val fa = IntArray(triangleCount)
        val fb = IntArray(triangleCount)
        val fc = IntArray(triangleCount)
        readFaces(typeBuf, indexBuf, triangleCount, fa, fb, fc)

        val triangleBones = if (hasTriangleBones == 1) {
            val buf = Rs2Buffer(src, triangleBonesDataOffset)
            IntArray(triangleCount) { buf.g1() }
        } else {
            null
        }
        val faceAlpha = if (hasAlpha == 1) {
            src.copyOfRange(alphaDataOffset, alphaDataOffset + triangleCount)
        } else {
            null
        }

        return Rs2Model(
            format = "old",
            vertexCount = vertexCount,
            faceCount = triangleCount,
            verticesX = vx,
            verticesY = vy,
            verticesZ = vz,
            faceA = fa,
            faceB = fb,
            faceC = fc,
            faceColors = faceColors,
            faceTextures = null,
            vertexBones = vertexBones,
            triangleBones = triangleBones,
            faceAlpha = faceAlpha,
        )
    }

    private fun readFaces(
        typeBuf: Rs2Buffer,
        indexBuf: Rs2Buffer,
        triangleCount: Int,
        fa: IntArray,
        fb: IntArray,
        fc: IntArray,
    ) {
        var a = 0
        var b = 0
        var c = 0
        var last = 0
        for (t in 0 until triangleCount) {
            when (val type = typeBuf.g1()) {
                1 -> {
                    a = indexBuf.gsmart() + last
                    b = indexBuf.gsmart() + a
                    c = indexBuf.gsmart() + b
                    last = c
                    fa[t] = a
                    fb[t] = b
                    fc[t] = c
                }
                2 -> {
                    b = c
                    c = indexBuf.gsmart() + last
                    last = c
                    fa[t] = a
                    fb[t] = b
                    fc[t] = c
                }
                3 -> {
                    a = c
                    c = indexBuf.gsmart() + last
                    last = c
                    fa[t] = a
                    fb[t] = b
                    fc[t] = c
                }
                4 -> {
                    val b0 = a
                    a = b
                    b = b0
                    c = indexBuf.gsmart() + last
                    last = c
                    fa[t] = a
                    fb[t] = b0
                    fc[t] = c
                }
                else -> throw IllegalArgumentException("Unknown face opcode $type at $t")
            }
        }
    }
}
