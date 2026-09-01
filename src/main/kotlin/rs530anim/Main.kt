package rs530anim

import rs530anim.anim.AnimBase
import rs530anim.anim.AnimFrame
import rs530anim.anim.AnimLibrary
import rs530anim.anim.ModelAnimator
import rs530anim.anim.TransformType
import rs530anim.anim.runFrameSelfTest
import rs530anim.cache.AppPaths
import rs530anim.cache.CacheSettings
import rs530anim.cache.Js5Store
import rs530anim.cache.LocalCache
import rs530anim.model.Rs2Model
import rs530anim.model.Rs2ModelLoader
import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val argv = if (args.isEmpty()) arrayOf("view", "132") else args
    val parsed = parseArgs(argv)
    when (parsed.command) {
        "selftest" -> runFrameSelfTest()
        "frame" -> {
            if (parsed.rest.size < 2) {
                System.err.println("Usage: rs530-anim-tool frame <frame.dat> <base.dat>")
                kotlin.system.exitProcess(1)
            }
            dumpFrame(Path.of(parsed.rest[0]), Path.of(parsed.rest[1]))
        }
        "model", "js5-model", "js5" -> loadAndDumpModel(parsed)
        "seq" -> dumpSeq(parsed)
        "apply" -> applySeq(parsed)
        "skins" -> MonkeySkins.print()
        "view" -> {
            val ids = if (parsed.rest.isEmpty()) listOf("132") else parsed.rest
            rs530anim.view.ModelViewer.open(ids)
        }
        else -> loadAndDumpModel(parsed.copy(command = "model", rest = listOf(args[0]) + parsed.rest))
    }
}

private data class ParsedArgs(
    val command: String,
    val host: String? = null,
    val port: Int? = null,
    val config: Path? = null,
    val rest: List<String> = emptyList(),
)

private fun parseArgs(args: Array<String>): ParsedArgs {
    var host: String? = null
    var port: Int? = null
    var config: Path? = null
    val rest = mutableListOf<String>()
    var i = 1
    while (i < args.size) {
        when (args[i]) {
            "--host" -> host = args.getOrNull(++i)
            "--port" -> port = args.getOrNull(++i)?.toInt()
            "--config" -> config = args.getOrNull(++i)?.let { Path.of(it) }
            else -> rest += args[i]
        }
        i++
    }
    return ParsedArgs(args[0], host, port, config, rest)
}

private fun printUsage() {
    System.err.println("Usage:")
    System.err.println("  rs530-anim-tool <model.dat>")
    System.err.println("  rs530-anim-tool model <model.dat>")
    System.err.println("  rs530-anim-tool model 132")
    System.err.println("  rs530-anim-tool js5-model 132 [--host play.2009scape.org] [--port 43595] [--config config.json]")
    System.err.println("Fetched files land in ${AppPaths.cacheDir()} (models/, bases/, frames/)")
    System.err.println("  rs530-anim-tool seq 220")
    System.err.println("  rs530-anim-tool apply 132 220")
    System.err.println("  rs530-anim-tool skins")
    System.err.println("  rs530-anim-tool view 132")
    System.err.println("  rs530-anim-tool view 132 220 0")
    System.err.println("  rs530-anim-tool frame <frame.dat> <base.dat>")
    System.err.println("  rs530-anim-tool selftest")
}

private fun settingsOf(parsed: ParsedArgs): CacheSettings {
    val loaded = CacheSettings.load(parsed.config)
    return loaded.copy(
        host = parsed.host ?: loaded.host,
        js5Port = parsed.port ?: loaded.js5Port,
    )
}

private fun loadAndDumpModel(parsed: ParsedArgs) {
    val target = parsed.rest.firstOrNull() ?: run {
        printUsage()
        kotlin.system.exitProcess(1)
    }
    val asPath = Path.of(target)
    val model: Rs2Model
    if (Files.isRegularFile(asPath)) {
        model = Rs2ModelLoader.load(asPath)
        println("source          : file $asPath")
    } else {
        val id = target.toIntOrNull()
            ?: throw IllegalArgumentException("not a file and not a model id: $target")
        val settings = CacheSettings.load(parsed.config).let {
            it.copy(
                host = parsed.host ?: it.host,
                js5Port = parsed.port ?: it.js5Port,
            )
        }
        val cache = LocalCache()
        val cached = cache.read(cache.modelPath(id))
        val bytes: ByteArray
        val path: Path
        if (cached != null) {
            bytes = cached
            path = cache.modelPath(id)
            println("source          : cache $path")
        } else {
            println("source          : js5 ${settings.host}:${settings.connectPort} archive=7 group=$id")
            val fetched = cache.model(id, settings)
            bytes = fetched.first
            path = fetched.second
            println("saved           : $path")
        }
        model = Rs2ModelLoader.decode(bytes)
    }
    printModel(model)
}

private fun printModel(model: Rs2Model) {
    val labels = model.uniqueVertexLabels()
    println("format          : ${model.format}")
    println("vertexCount     : ${model.vertexCount}")
    println("faceCount       : ${model.faceCount}")
    println("hasVertexBones  : ${model.vertexBones != null}")
    println("hasTriangleBones: ${model.triangleBones != null}")
    println("uniqueVLabels   : ${labels.size}  $labels")
    if (labels.isNotEmpty()) {
        println("label → verts   :")
        for (label in labels) {
            println("  $label → ${model.vertexCountForLabel(label)}")
        }
    }
    if (model.triangleBones != null) {
        println("uniqueTLabels   : ${model.uniqueFaceLabels()}")
    }
}

private fun dumpSeq(parsed: ParsedArgs) {
    val id = parsed.rest.firstOrNull()?.toIntOrNull()
        ?: run {
            System.err.println("Usage: rs530-anim-tool seq <seqId>")
            kotlin.system.exitProcess(1)
        }
    Js5Store(settingsOf(parsed)).use { store ->
        val seq = AnimLibrary.loadSeq(store, id)
        println("seq             : $id")
        println("frames          : ${seq.length}")
        println("looptype        : ${seq.looptype}")
        println("priority        : ${seq.priority}")
        println("replayoff       : ${seq.replayoff}")
        println("saved           : ${store.cache.seqPath(id)}")
        for (i in 0 until seq.length) {
            println(
                "  [$i] frameset=${seq.framesetId(i)} frame=${seq.frameIndex(i)} delay=${seq.delays[i]} ticks",
            )
        }
    }
}

private fun applySeq(parsed: ParsedArgs) {
    val modelId = parsed.rest.getOrNull(0)?.toIntOrNull()
    val seqId = parsed.rest.getOrNull(1)?.toIntOrNull()
    if (modelId == null || seqId == null) {
        System.err.println("Usage: rs530-anim-tool apply <modelId> <seqId>")
        kotlin.system.exitProcess(1)
    }
    val frameNo = parsed.rest.getOrNull(2)?.toIntOrNull() ?: 0
    Js5Store(settingsOf(parsed)).use { store ->
        val model = Rs2ModelLoader.decode(store.model(modelId))
        val seq = AnimLibrary.loadSeq(store, seqId)
        require(seq.length > 0) { "seq $seqId has no frames" }
        val idx = frameNo.coerceIn(0, seq.length - 1)
        val packed = seq.frames[idx]
        val frame = AnimLibrary.frameOf(store, packed)
        val before = (0 until minOf(4, model.vertexCount)).map {
            Triple(model.verticesX[it], model.verticesY[it], model.verticesZ[it])
        }
        val anim = ModelAnimator(model)
        anim.apply(frame)
        println("model           : $modelId  verts=${model.vertexCount}")
        println("seq             : $seqId  frame $idx/${seq.length} packed=0x${packed.toString(16)}")
        println("frameset        : ${seq.framesetId(idx)} file=${seq.frameIndex(idx)} base=${frame.base.id}")
        println("groups          : ${frame.length}")
        for (i in 0 until frame.length) {
            val slot = frame.indices[i].toInt()
            println(
                "  slot $slot ${TransformType.nameOf(frame.base.types[slot])} " +
                    "xyz=${frame.x[i]},${frame.y[i]},${frame.z[i]} " +
                    "labels=${frame.base.bones[slot].contentToString()}",
            )
        }
        println("origin after    : ${anim.originX},${anim.originY},${anim.originZ}")
        println("verts 0..3 before → after:")
        for (i in before.indices) {
            println(
                "  [$i] ${before[i]} → ${Triple(model.verticesX[i], model.verticesY[i], model.verticesZ[i])}",
            )
        }
    }
}

private fun dumpFrame(framePath: Path, basePath: Path) {
    val frameBytes = Files.readAllBytes(framePath)
    val baseId = ((frameBytes[0].toInt() and 0xFF) shl 8) or (frameBytes[1].toInt() and 0xFF)
    val base = AnimBase.decode(baseId, Files.readAllBytes(basePath))
    val frame = AnimFrame.decode(frameBytes, base)
    println("frame           : ${framePath.toAbsolutePath()}")
    println("baseId          : $baseId  (file ${base.id})")
    println("base.transforms : ${base.transforms}")
    println("used groups     : ${frame.length}")
    for (i in 0 until frame.length) {
        val slot = frame.indices[i].toInt()
        println(
            "  [$i] slot=$slot type=${TransformType.nameOf(base.types[slot])} " +
                "xyz=${frame.x[i]},${frame.y[i]},${frame.z[i]} " +
                "flags=${frame.flags[i]} prevOrigin=${frame.prevOriginIndices[i]} " +
                "labels=${base.bones[slot].contentToString()}",
        )
    }
}
