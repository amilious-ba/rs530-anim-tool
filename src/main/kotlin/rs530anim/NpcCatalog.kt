package rs530anim

data class NpcRow(
    val id: Int,
    val name: String,
    val attack: Int,
    val block: Int,
    val death: Int,
    val range: Int,
    val models: List<Int>,
) {
    fun seqIds(): List<Int> = listOf(attack, block, death, range).filter { it > 0 }.distinct()
}

object NpcCatalog {
    private val extraModels = mapOf(
        132 to listOf(3004),
        4344 to listOf(3004),
        1455 to listOf(4821, 4828, 4833),
        1456 to listOf(4821, 4828, 4831),
        1457 to listOf(4821, 4828, 4831),
        1465 to listOf(4817, 4824),
        1466 to listOf(4817, 4824),
        1467 to listOf(4817, 4824),
    )

    val all: List<NpcRow> by lazy { load() }

    private val byId: Map<Int, NpcRow> by lazy { all.associateBy { it.id } }

    fun get(id: Int): NpcRow? = byId[id]

    fun modelsFor(id: Int): List<Int> = extraModels[id] ?: listOf(id)

    fun search(q: String): List<NpcRow> {
        if (q.isBlank()) return all
        val n = q.toIntOrNull()
        if (n != null) return all.filter { it.id == n || it.seqIds().contains(n) }
        val needle = q.lowercase()
        return all.filter { it.name.lowercase().contains(needle) }
    }

    fun printNpcs(q: String) {
        val rows = search(q)
        println("id     name                             atk   blk   death range models")
        for (r in rows.take(200)) {
            println(
                "%-6d %-32s %-5d %-5d %-5d %-5d %s".format(
                    r.id, r.name.take(32), r.attack, r.block, r.death, r.range,
                    modelsFor(r.id).joinToString("+"),
                ),
            )
        }
        if (rows.size > 200) println("… ${rows.size - 200} more (narrow the filter)")
        println("${rows.size} npcs")
    }

    data class SeqRef(val id: Int, val label: String) {
        override fun toString(): String = if (label.isBlank()) id.toString() else "$id  $label"
    }

    fun sequencesForModels(modelIds: Collection<Int>): List<SeqRef> {
        val ids = modelIds.toSet()
        val names = linkedMapOf<Int, MutableList<String>>()
        fun add(seq: Int?, role: String) {
            if (seq == null || seq <= 0) return
            names.getOrPut(seq) { mutableListOf() }.add(role)
        }
        for (s in MonkeySkins.all) {
            val models = modelsFor(s.modelId).toSet() + s.modelId
            if (models.none { it in ids }) continue
            add(s.attack, "${s.name} attack")
            add(s.block, "${s.name} block")
            add(s.death, "${s.name} death")
            add(s.sleep, "${s.name} sleep")
            add(s.wake, "${s.name} wake")
        }
        for (r in all) {
            val models = modelsFor(r.id).toSet() + r.id
            if (models.none { it in ids }) continue
            add(r.attack, "${r.name} attack")
            add(r.block, "${r.name} block")
            add(r.death, "${r.name} death")
            add(r.range, "${r.name} range")
        }
        return names.entries
            .sortedBy { it.key }
            .map { (id, roles) -> SeqRef(id, roles.distinct().joinToString(" / ")) }
    }

    fun printAnims(q: String) {
        val rows = if (q.isBlank()) all else search(q)
        val map = linkedMapOf<Int, MutableList<String>>()
        for (r in rows) {
            if (r.attack > 0) map.getOrPut(r.attack) { mutableListOf() }.add("${r.id} ${r.name} attack")
            if (r.block > 0) map.getOrPut(r.block) { mutableListOf() }.add("${r.id} ${r.name} block")
            if (r.death > 0) map.getOrPut(r.death) { mutableListOf() }.add("${r.id} ${r.name} death")
            if (r.range > 0) map.getOrPut(r.range) { mutableListOf() }.add("${r.id} ${r.name} range")
        }
        val ids = if (q.toIntOrNull() != null && map.containsKey(q.toInt())) {
            listOf(q.toInt())
        } else {
            map.keys.sorted()
        }
        println("seq    used by")
        for (id in ids) {
            val uses = map[id] ?: continue
            println("%-6d %s".format(id, uses.take(3).joinToString(" | ")))
            if (uses.size > 3) println("       +${uses.size - 3} more")
        }
        println("${ids.size} sequences")
    }

    private fun load(): List<NpcRow> {
        val src = NpcCatalog::class.java.getResourceAsStream("/rs530anim/npcs.tsv")
            ?: error("npcs.tsv missing")
        return src.bufferedReader().useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 6) return@mapNotNull null
                val id = p[0].toIntOrNull() ?: return@mapNotNull null
                NpcRow(id, p[1], p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), extraModels[id] ?: listOf(id))
            }.toList()
        }
    }
}
