package rs530anim.ai

import rs530anim.anim.AnimFrame
import rs530anim.anim.TransformType
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

data class TrackPatch(
    val frame: Int,
    val label: Int,
    val type: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val delay: Int? = null,
)

object GrokAnimClient {
    private const val ENDPOINT = "https://api.x.ai/v1/chat/completions"

    fun describeSequence(
        seqId: Int?,
        baseId: Int?,
        labels: List<Int>,
        selectedLabel: Int?,
        frames: List<AnimFrame>,
        delays: IntArray,
    ): String {
        val sb = StringBuilder()
        sb.append("seq=").append(seqId ?: -1)
        sb.append(" base=").append(baseId ?: -1)
        sb.append(" labels=").append(labels.joinToString(","))
        sb.append(" selected=").append(selectedLabel ?: labels.firstOrNull() ?: 0)
        sb.append(" frameCount=").append(frames.size).append('\n')
        sb.append(playbackNotes())
        frames.forEachIndexed { i, frame ->
            val ticks = delays.getOrElse(i) { 5 }
            sb.append("frame ").append(i).append(" delay=").append(ticks)
            for (lab in labels) {
                for (type in intArrayOf(TransformType.TRANSLATE, TransformType.ROTATE, TransformType.SCALE)) {
                    val v = frame.valuesForLabel(lab, type) ?: continue
                    val def = if (type == TransformType.SCALE) 128 else 0
                    if (v.first == def && v.second == def && v.third == def) continue
                    sb.append(" | vskin ").append(lab).append(' ')
                        .append(TransformType.nameOf(type))
                        .append(" x=").append(v.first)
                        .append(" y=").append(v.second)
                        .append(" z=").append(v.third)
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    fun describeFullGrid(
        seqId: Int?,
        baseId: Int?,
        labels: List<Int>,
        frames: List<AnimFrame>,
        delays: IntArray,
    ): String {
        val sb = StringBuilder()
        val base = frames.firstOrNull()?.base
        sb.append("seq=").append(seqId ?: -1)
        sb.append(" base=").append(baseId ?: -1)
        sb.append(" labels=").append(labels.joinToString(","))
        sb.append(" frameCount=").append(frames.size).append('\n')
        sb.append(playbackNotes())
        if (base != null) {
            sb.append("AnimBase slots (one xyz hits every label in the slot):\n")
            for (slot in base.types.indices) {
                sb.append("  slot ").append(slot).append(' ')
                    .append(TransformType.nameOf(base.types[slot]))
                    .append(" labels=[").append(base.bones[slot].joinToString(",")).append("]\n")
            }
        }
        sb.append("Full grid (every vskin that has a slot):\n")
        frames.forEachIndexed { i, frame ->
            sb.append("frame ").append(i).append(" delay=").append(delays.getOrElse(i) { 5 }).append('\n')
            for (lab in labels) {
                for (type in intArrayOf(TransformType.TRANSLATE, TransformType.ROTATE, TransformType.SCALE)) {
                    val v = frame.valuesForLabel(lab, type) ?: continue
                    sb.append("  vskin ").append(lab).append(' ')
                        .append(TransformType.nameOf(type))
                        .append(" x=").append(v.first)
                        .append(" y=").append(v.second)
                        .append(" z=").append(v.third)
                        .append('\n')
                }
            }
        }
        return sb.toString()
    }

    fun complete(apiKey: String, model: String, system: String, user: String): String {
        val body = StringBuilder()
        body.append('{')
        body.append("\"model\":\"").append(escape(model)).append("\",")
        body.append("\"temperature\":0.4,")
        body.append("\"messages\":[")
        body.append("{\"role\":\"system\",\"content\":\"").append(escape(system)).append("\"},")
        body.append("{\"role\":\"user\",\"content\":\"").append(escape(user)).append("\"}")
        body.append("]}")
        val conn = URI(ENDPOINT).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 20_000
        conn.readTimeout = 120_000
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("xAI HTTP $code: ${text.take(400)}")
        return extractMessage(text)
    }

    fun parsePatches(raw: String, frameCount: Int): List<TrackPatch> {
        val json = raw.substringAfter('{', missingDelimiterValue = "").let {
            if (it.isEmpty()) raw else "{$it"
        }
        val patches = mutableListOf<TrackPatch>()
        val obj = Regex("\\{[^{}]+\\}")
        for (m in obj.findAll(json)) {
            val chunk = m.value
            val frame = num(chunk, "frame") ?: continue
            val label = num(chunk, "label") ?: continue
            val typeName = str(chunk, "type") ?: continue
            val type = when (typeName.lowercase()) {
                "pos", "translate", "position" -> TransformType.TRANSLATE
                "rot", "rotate", "rotation" -> TransformType.ROTATE
                "scale" -> TransformType.SCALE
                else -> continue
            }
            val x = num(chunk, "x") ?: 0
            val y = num(chunk, "y") ?: 0
            val z = num(chunk, "z") ?: 0
            val delay = num(chunk, "delay")
            if (frame !in 0 until frameCount) continue
            patches += TrackPatch(frame, label, type, x, y, z, delay)
        }
        return patches
    }

    private fun num(chunk: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(chunk)?.groupValues?.get(1)?.toIntOrNull()

    private fun str(chunk: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"").find(chunk)?.groupValues?.get(1)

    private fun extractMessage(response: String): String {
        val key = "\"content\""
        val at = response.indexOf(key)
        if (at < 0) return response
        val colon = response.indexOf(':', at + key.length)
        val startQuote = response.indexOf('"', colon + 1)
        if (startQuote < 0) return response
        val out = StringBuilder()
        var i = startQuote + 1
        while (i < response.length) {
            val c = response[i]
            if (c == '\\' && i + 1 < response.length) {
                when (response[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    else -> out.append(response[i + 1])
                }
                i += 2
                continue
            }
            if (c == '"') break
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun escape(text: String): String = buildString(text.length + 16) {
        for (c in text) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    fun playbackNotes(): String = """
        Timing: delay is in client ticks. 1 tick = 20 ms. delay 5 = 100 ms. Typical attack frames use 3-8 ticks.
        Execution (SoftwareModel.method4569), every frame from bind pose:
          type 0 origin: average the verts of those labels; that point is the pivot for later groups.
          type 1 translate: add x/y/z to verts of those labels only.
          type 2 rotate: rotate those labels around the current origin. Units 0..2047, 2048 = 360 deg.
          type 3 scale: scale those labels around the origin; 128 = 1.0.
        Y is down. Raising a limb is negative translate Y, not moving every label.
        vskin 0 is usually the origin locator, not the floor and not the whole NPC.
        Do NOT keyframe origin slots or translate vskin 0 unless the user asks to move the root.
        Do NOT copy the same xyz onto every vskin. Animate limbs with rotate on their own slots.
        Keep translate magnitudes small (tens, not thousands). Rotate deltas from rest about 40-240.
    """.trimIndent() + "\n"

    val systemPrompt: String = """
        You edit RuneScape revision 530 vskin animation tracks.
        Follow the playback notes in the user message.
        Never return an empty patches array.
        Prefer rotate on a single limb label. Do not translate vskin 0 or origin slots.
        Do not write the same xyz on every label.
        If the user says selected group, use selected=.
        JSON only:
        {"patches":[{"frame":2,"label":1,"type":"rotate","x":80,"y":0,"z":0,"delay":5}]}
    """.trimIndent()

    val systemPromptNew: String = """
        You author a NEW revision-530 label animation on the given AnimBase.
        Follow the playback notes and slot list in the user message.
        Animate by rotating individual limb labels. Never slide the whole NPC.
        Forbidden unless the user asks: origin keys, translate on vskin 0, same xyz on every vskin.
        Frame count 4..12. Delays are ticks (20 ms each), use 4-8.
        JSON only:
        {"frameCount":8,"patches":[{"frame":0,"label":1,"type":"rotate","x":24,"y":0,"z":0,"delay":5}]}
        Include several limb rotates across frames. Never return empty patches.
    """.trimIndent()

    fun sanitizePatches(patches: List<TrackPatch>, allowRoot: Boolean = false): List<TrackPatch> {
        return patches.filter { p ->
            if (p.type == TransformType.ORIGIN) return@filter false
            if (!allowRoot && p.label == 0 && p.type == TransformType.TRANSLATE) return@filter false
            val mag = kotlin.math.abs(p.x) + kotlin.math.abs(p.y) + kotlin.math.abs(p.z)
            if (p.type == TransformType.TRANSLATE && mag > 400) return@filter false
            true
        }
    }

    fun parseFrameCount(raw: String, fallback: Int): Int =
        Regex("\"frameCount\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(4, 12) ?: fallback
}
