package rs530anim.extras

/**
 * On-disk extras sequence. Not packed into dat2.
 *
 * extras/seq/<id>.json
 * extras/frames/<id>/<index>.dat   raw AnimFrame, first u16 = existing base id
 */
data class SeqExtras(
    val id: Int,
    val baseId: Int,
    val loop: Int,
    val priority: Int,
    val frames: List<Int>,
    val delays: List<Int>,
) {
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"id\": $id,\n")
        append("  \"baseId\": $baseId,\n")
        append("  \"loop\": $loop,\n")
        append("  \"priority\": $priority,\n")
        append("  \"frames\": [${frames.joinToString(", ")}],\n")
        append("  \"delays\": [${delays.joinToString(", ")}]\n")
        append("}\n")
    }

    companion object {
        fun fromJson(text: String): SeqExtras {
            fun intField(name: String): Int {
                val m = Regex("\"$name\"\\s*:\\s*(-?\\d+)").find(text)
                    ?: error("extras json missing $name")
                return m.groupValues[1].toInt()
            }
            fun intList(name: String): List<Int> {
                val m = Regex("\"$name\"\\s*:\\s*\\[([^]]*)]").find(text)
                    ?: error("extras json missing $name")
                val body = m.groupValues[1].trim()
                if (body.isEmpty()) return emptyList()
                return body.split(',').map { it.trim().toInt() }
            }
            return SeqExtras(
                id = intField("id"),
                baseId = intField("baseId"),
                loop = intField("loop"),
                priority = intField("priority"),
                frames = intList("frames"),
                delays = intList("delays"),
            )
        }
    }
}
