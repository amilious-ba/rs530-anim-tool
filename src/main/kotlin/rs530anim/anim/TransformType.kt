package rs530anim.anim

/** AnimBase.types[slot] — same numbers SoftwareModel.method4569 switches on. */
object TransformType {
    const val ORIGIN = 0
    const val TRANSLATE = 1
    const val ROTATE = 2
    const val SCALE = 3
    const val ALPHA = 5
    const val COLOR = 7

    fun nameOf(type: Int): String = when (type) {
        ORIGIN -> "origin"
        TRANSLATE -> "translate"
        ROTATE -> "rotate"
        SCALE -> "scale"
        ALPHA -> "alpha"
        COLOR -> "color"
        else -> "type$type"
    }
}
