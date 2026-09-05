package rs530anim.ai

import rs530anim.anim.AnimBase
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
        sb.append("REFERENCE stock clip — real 530 values for this NPC. Match this scale and which labels move.\n")
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

    fun characterContext(
        npcId: Int?,
        npcName: String?,
        modelIds: List<Int>,
        labels: List<Int>,
        vertsOf: (Int) -> Int,
    ): String = buildString {
        append("Game: 2009scape / RuneScape revision 530. Legacy vskin labels, not OSRS skeletal.\n")
        append("NPC id=").append(npcId ?: -1)
        append(" name=\"").append(npcName ?: "unknown").append('"')
        append(" model ids=").append(modelIds.joinToString("+")).append('\n')
        append("This is that NPC's world mesh. Playback is identical to the 530 client.\n")
        append("Biggest non-zero vskin is usually the body; vskin 0 is a tiny origin locator.\n")
        append("Label sizes:\n")
        for (lab in labels) {
            append("  vskin ").append(lab).append(" verts=").append(vertsOf(lab)).append('\n')
        }
        append("Turn around = rotate y on the body label (1024 ≈ 180 degrees).\n")
        append("Butt shake = alternate small rotate x/z plus tiny translate z on body/hips each frame.\n")
    }

    fun playbackNotes(): String = """
        Timing: delay is client ticks. 1 tick = 20 ms. delay 5 = 100 ms. Typical frames use 3-8 ticks.
        Client playback (Model.method4553 + SoftwareModel.method4569), every frame from bind pose:
          if prevOrigin is set, type 0 origin: average verts of that slot's labels → pivot
          type 1 translate: add xyz to EVERY label listed on that AnimBase slot
          type 2 rotate: rotate EVERY label on that slot around the pivot. 0..2047, 2048 = 360 deg
          type 3 scale: scale those labels around the pivot; 128 = 1.0
        A patch names a vskin+type. The editor writes the tightest slot that contains that vskin.
        The client then applies that slot to all labels on it. If the slot lists 1,4,7 they all move.
        Look at the slot list. Prefer a vskin that has its own small slot. A shared body slot turns the whole mesh.
        Y is down. Raise = negative translate Y. Turn around = rotate y ≈ 1024 on the body slot.
        Do not emit origin keys or translate vskin 0 unless asked. Do not emit one xyz for every vskin.
        Translate tens to low hundreds. Rotate deltas 40-1024.
    """.trimIndent() + "\n"

    val systemPrompt: String = """
        You edit revision-530 AnimFrame groups the same way the 2009scape client plays them.
        Follow the playback notes and AnimBase slot list. One xyz per slot is applied to every label on that slot.
        Never return an empty patches array.
        Use rotate and translate where needed. Do not translate vskin 0.
        If the user says selected group, patch that vskin's tightest slot.
        JSON only:
        {"patches":[{"frame":2,"label":1,"type":"rotate","x":80,"y":0,"z":0,"delay":5}]}
    """.trimIndent()

    val systemPromptNew: String = """
        You author a NEW revision-530 AnimFrame clip on the given AnimBase.
        The client will play it with method4553/method4569: bind pose, origin pivot, then each slot's xyz on all labels in that slot.
        Follow the slot list. A patch on a shared slot moves every label in it. That is correct client behavior.
        Use rotate y on the body slot to turn (1024 ≈ 180 deg). Use small translates only on slots that are not the whole mesh.
        Forbidden: origin keys, translate vskin 0, duplicate xyz on every vskin, huge root translates.
        Frame count 4..12. Delays are ticks (20 ms), use 4-8.
        The included grid is a real stock clip for scale, not to replay.
        JSON only:
        {"frameCount":8,"patches":[{"frame":0,"label":1,"type":"rotate","x":0,"y":0,"z":0,"delay":5},{"frame":3,"label":1,"type":"rotate","x":80,"y":1024,"z":0,"delay":5},{"frame":7,"label":1,"type":"rotate","x":0,"y":0,"z":0,"delay":5}]}
    """.trimIndent()

    fun bodyLabel(base: AnimBase, labels: List<Int>, vertsOf: (Int) -> Int): Int? {
        return labels
            .filter { it > 0 && base.slotFor(it, TransformType.ROTATE) != null }
            .maxByOrNull { vertsOf(it) }
            ?: labels.filter { it > 0 }.maxByOrNull { vertsOf(it) }
    }

    fun enforcePromptHints(
        prompt: String,
        patches: List<TrackPatch>,
        base: AnimBase,
        labels: List<Int>,
        frameCount: Int,
        vertsOf: (Int) -> Int,
    ): List<TrackPatch> {
        val text = prompt.lowercase()
        val wantsTurn = listOf("turn", "spin", "around", "180", "360").any { it in text }
        val wantsShake = listOf("shake", "wiggle", "butt", "hip", "twerk").any { it in text }
        if (!wantsTurn && !wantsShake) return patches
        val body = bodyLabel(base, labels, vertsOf) ?: return patches
        val out = patches.toMutableList()
        val maxRotY = patches.filter { it.label == body && it.type == TransformType.ROTATE }
            .maxOfOrNull { kotlin.math.abs(it.y) } ?: 0
        if (wantsTurn && maxRotY < 400 && base.slotFor(body, TransformType.ROTATE) != null) {
            val mid = (frameCount - 1).coerceAtLeast(1)
            val half = mid / 2
            out.removeAll { it.label == body && it.type == TransformType.ROTATE }
            for (f in 0 until frameCount) {
                val y = when {
                    f <= half -> (1024.0 * f / half.coerceAtLeast(1)).toInt()
                    else -> (1024.0 * (mid - f) / (mid - half).coerceAtLeast(1)).toInt()
                }.coerceIn(0, 2047)
                val shake = if (wantsShake && f in 1 until mid) (if (f % 2 == 0) 90 else -90) else 0
                out += TrackPatch(f, body, TransformType.ROTATE, shake, y, shake / 2, 5)
            }
        } else if (wantsShake && base.slotFor(body, TransformType.ROTATE) != null) {
            for (f in 1 until (frameCount - 1).coerceAtLeast(1)) {
                val s = if (f % 2 == 0) 70 else -70
                out += TrackPatch(f, body, TransformType.ROTATE, s, 0, s / 2, 5)
                if (base.slotFor(body, TransformType.TRANSLATE) != null) {
                    out += TrackPatch(f, body, TransformType.TRANSLATE, 0, 0, s / 3, 5)
                }
            }
        }
        out.removeAll { it.type == TransformType.TRANSLATE && kotlin.math.abs(it.y) > 20 && it.label == body }
        return out.sortedWith(compareBy({ it.frame }, { it.label }, { it.type }))
    }

    fun sanitizePatches(patches: List<TrackPatch>, allowRoot: Boolean = false): List<TrackPatch> {
        return patches.filter { p ->
            if (p.type == TransformType.ORIGIN) return@filter false
            if (!allowRoot && p.label == 0 && p.type == TransformType.TRANSLATE) return@filter false
            val mag = kotlin.math.abs(p.x) + kotlin.math.abs(p.y) + kotlin.math.abs(p.z)
            if (p.type == TransformType.TRANSLATE && mag > 800) return@filter false
            true
        }
    }

    fun parseFrameCount(raw: String, fallback: Int): Int =
        Regex("\"frameCount\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?.coerceIn(4, 12) ?: fallback
}
