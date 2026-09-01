package rs530anim

/** NPC / model ids the user is actually animating. Not the same as seq ids. */
data class MonkeySkin(
    val modelId: Int,
    val name: String,
    val attack: Int,
    val block: Int,
    val death: Int,
    val sleep: Int? = null,
    val wake: Int? = null,
    val deathOnSleep: Boolean = false,
)

object MonkeySkins {
    val all = listOf(
        MonkeySkin(4344, "Gigos", attack = 220, block = 221, death = 223, deathOnSleep = true),
        MonkeySkin(132, "Gigos", attack = 220, block = 221, death = 223, deathOnSleep = true),
        MonkeySkin(1457, "Archer Gigos", attack = 1394, block = 1393, death = 1384, sleep = 1390, wake = 1389),
        MonkeySkin(1456, "Archer Gigos", attack = 1394, block = 1393, death = 1384, sleep = 1390, wake = 1389),
        MonkeySkin(1455, "Ninja Gigos", attack = 1392, block = 1393, death = 1384, sleep = 1390, wake = 1389),
        MonkeySkin(1467, "Zombie Gigos", attack = 1392, block = 1393, death = 1384, sleep = 1390, wake = 1389),
        MonkeySkin(1466, "Zombie Gigos", attack = 1392, block = 1393, death = 1384, sleep = 1390, wake = 1389),
        MonkeySkin(1465, "Zombie Gigos", attack = 1383, block = 1393, death = 1384, sleep = 1390, wake = 1389),
    )

    fun byModel(id: Int): List<MonkeySkin> = all.filter { it.modelId == id }

    fun resolve(spec: String): List<Int> {
        val key = spec.trim().lowercase()
        return when (key) {
            "", "gigos", "full", "body", "archer" -> listOf(1456)
            "head" -> listOf(132)
            "ninja" -> listOf(1455)
            "zombie" -> listOf(1467)
            else -> spec.split('+', ',').mapNotNull { part ->
                val p = part.trim().lowercase()
                p.toIntOrNull() ?: when (p) {
                    "head" -> 132
                    "gigos", "full", "archer", "body" -> 1456
                    "ninja" -> 1455
                    "zombie" -> 1467
                    else -> null
                }
            }.ifEmpty { listOf(1456) }
        }
    }

    fun print() {
        println("model  name            attack block death sleep wake")
        for (s in all) {
            println(
                "%-6d %-15s %-6d %-5d %-5d %-5s %-4s%s".format(
                    s.modelId,
                    s.name,
                    s.attack,
                    s.block,
                    s.death,
                    s.sleep?.toString() ?: "-",
                    s.wake?.toString() ?: "-",
                    if (s.deathOnSleep) "  deathOnSleep" else "",
                ),
            )
        }
        println("view:  rs530-anim-tool view <modelId>")
        println("apply: rs530-anim-tool apply <modelId> <seqId>")
    }
}
