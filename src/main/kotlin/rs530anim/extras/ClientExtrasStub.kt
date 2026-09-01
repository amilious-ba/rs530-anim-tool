package rs530anim.extras

/**
 * Tiny client-side recipe. Do not copy JavaFX. Drop ExtrasStore + SeqExtras +
 * AnimFrame/AnimBase/Rs2Buffer into the Amilious client (or depend on this
 * source set) and hook:
 *
 *   SeqTypeList.get(id):
 *     if (ExtrasStore.exists(id, extrasRoot)) {
 *       val (def, _) = ExtrasStore.load(id, { baseId -> AnimBaseList.get(baseId) }, extrasRoot)
 *       return SeqType(def.id, packedFrames(def), def.delays.toIntArray(), def.loop, def.priority, -1)
 *     }
 *     // existing cache path
 *
 *   packedFrames: store extras frames as frameset extrasId, file index i
 *   so SeqType.frames[i] = (extrasSeqId shl 16) or i
 *
 *   AnimFrameset.get(setId):
 *     if extras/frames/<setId>/0.dat exists, decode those files with the
 *     base id in the first u16. Reuse AnimBaseList.get(baseId) — never a new base.
 *
 * extrasRoot is a folder next to the client, same layout this tool writes.
 */
object ClientExtrasStub
