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
    val vertexBones: IntArray?,
    val triangleBones: IntArray?,
) {
    val boneVertices: Array<IntArray> = buildBoneVertices()

    fun uniqueVertexLabels(): List<Int> {
        if (vertexBones == null) return emptyList()
        return vertexBones.toSet().sorted()
    }

    fun uniqueFaceLabels(): List<Int> {
        if (triangleBones == null) return emptyList()
        return triangleBones.toSet().sorted()
    }

    fun vertexCountForLabel(label: Int): Int {
        if (label < 0 || label >= boneVertices.size) return 0
        return boneVertices[label].size
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
