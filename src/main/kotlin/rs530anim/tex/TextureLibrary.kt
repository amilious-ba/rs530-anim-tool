package rs530anim.tex

import javafx.scene.image.Image
import rs530anim.cache.Js5Archives
import rs530anim.cache.Js5Store

object TextureLibrary {
    private val images = HashMap<Int, Image>()

    fun image(id: Int, store: Js5Store? = null): Image? {
        images[id]?.let { return it }
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
            System.err.println("texture $id graph: ${e.message}")
            null
        }
    }
}
