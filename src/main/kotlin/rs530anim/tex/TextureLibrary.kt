package rs530anim.tex

import javafx.scene.image.Image
import rs530anim.cache.Js5Archives
import rs530anim.cache.Js5Store

object TextureLibrary {
    private val images = HashMap<Int, Image>()
    private val failed = HashSet<Int>()

    fun image(id: Int, store: Js5Store? = null): Image? {
        images[id]?.let { return it }
        if (id in failed) return null
        val bytes = store?.let {
            try { it.textureBytes(id) } catch (e: Exception) { null }
        } ?: TextureLibrary::class.java.getResourceAsStream("/rs530anim/textures/$id.dat")?.readBytes()
        if (bytes == null) return null
        return try {
            val graph = TextureGraph.decode(bytes)
            val img = graph.raster(128)
            println("texture $id graph ops=${graph.ops.size}")
            images[id] = img
            img
        } catch (e: Exception) {
            failed += id
            System.err.println("texture $id graph: ${e.message}")
            null
        }
    }
}
