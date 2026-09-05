package rs530anim.model

/**
 * Fields we need from rt4.RawModel for dump + later preview.
 * vertexBones == vskin labels. triangleBones == tskin labels.
 * boneVertices is RawModel.createBones(): label → vertex indices.
 */
data class Rs2Model(
    val format: String,
    val vertexCount: Int,
    val faceCount: Int,
    val verticesX: IntArray,
    val verticesY: IntArray,
    val verticesZ: IntArray,
    val faceA: IntArray,
    val faceB: IntArray,
    val faceC: IntArray,
    val faceColors: ShortArray,
    val faceTextures: ShortArray?,
    val vertexBones: IntArray?,
    val triangleBones: IntArray?,
    val textureTypes: ByteArray? = null,
    val textureP: ShortArray? = null,
    val textureM: ShortArray? = null,
    val textureN: ShortArray? = null,
    val textureIndex: ByteArray? = null,
    val textureScaleX: ShortArray? = null,
    val textureScaleY: ShortArray? = null,
    val textureScaleZ: ShortArray? = null,
    val textureRotY: ByteArray? = null,
    val textureDir: ByteArray? = null,
    val textureOff: ByteArray? = null,
) {
    val boneVertices: Array<IntArray> = buildBoneVertices()

    fun uniqueVertexLabels(): List<Int> {
        if (vertexBones == null) return emptyList()
        return vertexBones.filter { it >= 0 }.toSet().sorted()
    }

    fun uniqueFaceLabels(): List<Int> {
        if (triangleBones == null) return emptyList()
        return triangleBones.toSet().sorted()
    }

    fun vertexCountForLabel(label: Int): Int {
        if (label < 0 || label >= boneVertices.size) return 0
        return boneVertices[label].size
    }

    /** Concatenate another mesh (NPC multi-model). Labels stay as stored. */
    fun attach(other: Rs2Model): Rs2Model {
        val vc = vertexCount + other.vertexCount
        val fc = faceCount + other.faceCount
        val vx = verticesX.copyOf(vc)
        val vy = verticesY.copyOf(vc)
        val vz = verticesZ.copyOf(vc)
        other.verticesX.copyInto(vx, vertexCount)
        other.verticesY.copyInto(vy, vertexCount)
        other.verticesZ.copyInto(vz, vertexCount)
        val fa = faceA.copyOf(fc)
        val fb = faceB.copyOf(fc)
        val fcA = faceC.copyOf(fc)
        for (i in 0 until other.faceCount) {
            fa[faceCount + i] = other.faceA[i] + vertexCount
            fb[faceCount + i] = other.faceB[i] + vertexCount
            fcA[faceCount + i] = other.faceC[i] + vertexCount
        }
        val colors = ShortArray(fc)
        faceColors.copyInto(colors)
        other.faceColors.copyInto(colors, faceCount)
        val textures = if (faceTextures == null && other.faceTextures == null) {
            null
        } else {
            ShortArray(fc) { i ->
                if (i < faceCount) {
                    faceTextures?.getOrNull(i) ?: -1
                } else {
                    other.faceTextures?.getOrNull(i - faceCount) ?: -1
                }
            }
        }
        val vBones = when {
            vertexBones == null && other.vertexBones == null -> null
            else -> {
                val out = IntArray(vc)
                vertexBones?.copyInto(out)
                other.vertexBones?.copyInto(out, vertexCount)
                out
            }
        }
        val tBones = when {
            triangleBones == null && other.triangleBones == null -> null
            else -> {
                val out = IntArray(fc)
                triangleBones?.copyInto(out)
                other.triangleBones?.copyInto(out, faceCount)
                out
            }
        }
        return Rs2Model(
            format = "$format+${other.format}",
            vertexCount = vc,
            faceCount = fc,
            verticesX = vx,
            verticesY = vy,
            verticesZ = vz,
            faceA = fa,
            faceB = fb,
            faceC = fcA,
            faceColors = colors,
            faceTextures = textures,
            vertexBones = vBones,
            triangleBones = tBones,
        )
    }

    private fun buildBoneVertices(): Array<IntArray> {
        if (vertexBones == null || vertexBones.isEmpty()) return emptyArray()
        val counts = IntArray(256)
        var max = 0
        for (bone in vertexBones) {
            counts[bone]++
            if (bone > max) max = bone
        }
        val groups = Array(max + 1) { IntArray(counts[it]) }
        val fill = IntArray(max + 1)
        for (i in vertexBones.indices) {
            val bone = vertexBones[i]
            groups[bone][fill[bone]++] = i
        }
        return groups
    }
}
