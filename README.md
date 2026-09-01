# rs530-anim-tool

Desktop editor for **revision 530 / 2009scape** label animations.

Not Blender. Not a cache packer. Not OSRS skeletal. Vertex labels (`vskin`)
are the handles. Stock monkey anims (220 throw, 223 death, 1392 ninja stand)
must keep working on the same mesh, so we **reuse model 132's existing labels
and its existing AnimBase** — we do not invent a new skeleton.

This repo is built one compiling slice at a time.

## Commands

```text
gradlew.bat run --args="samples/132.dat"
gradlew.bat run --args="model 132"
gradlew.bat run --args="js5-model 132 --host play.2009scape.org --port 43595"
gradlew.bat run --args="selftest"
```

Default JS5 host is the live 2009scape world: `play.2009scape.org:43595` (same cache family as yours).
`model 132` talks JS5 like the client: `p1(15)+p4(530)`, archive 7 / group 132.
Fetched files go next to the app in `cache/models/<id>.dat` (also `cache/bases/`, `cache/frames/`). A second load uses the file and skips JS5.

## Step 1 — model dump

## Step 2 — AnimBase / AnimFrame encode+decode

`selftest` builds a tiny base + frame, writes client bytes, reads them back.
`frame` dumps a real extracted frame against its AnimBase file (first u16 of
the frame is baseId; the base file is cache anim bases group 0 / file baseId).

Windows / IntelliJ:

1. Open this folder as a Gradle project (IntelliJ will offer to generate the wrapper).
2. Drop `132.dat` (model 132 extracted from a 530 cache) into `samples/`.
3. Run:

```text
gradlew.bat run --args="samples/132.dat"
```

Expected stdout shape:

```text
file            : ...\samples\132.dat
format          : old | type1
vertexCount     : <n>
faceCount       : <n>
hasVertexLabels : true
uniqueVLabels   : k  [0, 1, 2, ...]
label → verts   :
  0 → ...
  1 → ...
```

`Rs2ModelLoader` ports `rt4.RawModel.decodeOld` / `decodeNew`.
`AnimBase` / `AnimFrame` port the Amilious constructors and a matching encoder.

---

## 1. Repo layout

```text
rs530-anim-tool/
  README.md
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  samples/                         # your extracted 132.dat lives here
  extras/                          # created on export (not packed into dat2)
    frames/<seqId>/<index>.dat
    seq/<seqId>.json
  src/main/kotlin/rs530anim/
    Main.kt
    model/
      Rs2Buffer.kt
      Rs2Model.kt
      Rs2ModelLoader.kt            # RawModel.decodeOld / decodeNew
    anim/
      AnimBase.kt
      AnimFrame.kt
      TransformType.kt
      FrameSelfTest.kt
    # later slices:
    #   view/   JavaFX orbit view, HSL flats, label highlight
    #   view/   JavaFX orbit view, HSL flats, label highlight
    #   edit/   timeline + per-label sliders
    #   export/ extras writer
  vendor/rt4/                      # pasted client sources (not compiled)
    Buffer.java                    # received
    NOTE.txt                       # still-needed list
```

Out of scope for this repo: IK, weight painting, cache packing, map/NPC
editors, gizmos, undo, multi-model scenes, a client loader implementation
beyond a README stub.

---

## 2. Exact export file formats

Custom ids live under an **extras folder next to the running client**, never
inside `main_file_cache.dat2`.

### `extras/seq/<seqId>.json`

One sequence definition. Frame ids in this file are **indices into this
sequence's own frame folder**, not packed cache frame ids.

```json
{
  "id": 90000,
  "baseId": 132,
  "loop": 0,
  "priority": 5,
  "frames": [0, 1, 2],
  "delays": [5, 5, 5]
}
```

| field      | type      | meaning |
|------------|-----------|---------|
| `id`       | int       | Sequence id the server/client will `animate(seqId)` with. Pick a high unused id. |
| `baseId`   | int       | Existing AnimBase id. For monkey 132 **reuse the base already used by 220 / 223 / 1392**. Do not emit a new base. |
| `loop`     | int       | SeqType `looptype` (0 default, 2 loop). |
| `priority` | int       | Optional. SeqType opcode 5. Default 5. |
| `frames`   | int[]     | Frame indices. File `extras/frames/<seqId>/<frames[i]>.dat`. |
| `delays`   | int[]     | Per-frame delay in **client ticks** (same unit SeqType opcode 1 stores). Same length as `frames`. |

`delays` are ticks, not milliseconds. The editor UI can show ms as `ticks * 20`.

No sounds, no offhand/mainhand, no frameset overlay, no exactmove overrides
in v1. Those SeqType opcodes stay at client defaults.

### `extras/frames/<seqId>/<index>.dat`

Raw **AnimFrame bytes**, same payload `new AnimFrame(bytes, base)` consumes.

```text
u16  baseId                  // must equal extras/seq/<seqId>.json baseId
u8   groupCount              // MUST equal AnimBase.transforms for that base
u8   attributes[groupCount]  // per AnimBase slot; 0 = group unused this frame
then packed signed-smarts    // one gsmart per set bit in attributes
                             // bit0 = x, bit1 = y, bit2 = z
                             // bits 3–4 = flags nibble stored on the frame
```

Encoder rules (must match `rt4.AnimFrame.<init>`):

- Groups are indexed in **AnimBase order**, not label-id order.
- `attributes == 0` → no xyz payload for that slot.
- Type 3 (scale) default when a bit is unset is **128**, not 0.
- Type 2 (rotate) values are stored as the client-packed form:
  `((v & 0xFF) << 3) + (v >> 8 & 0x7)` is what the client applies after read.
  The encoder writes the pre-transform gsmart the client expects to decode
  into that. Step 3 will copy the exact pack/unpack from `AnimFrame`.
- Types 5 (alpha) and 7 (color) set the frame-level flags; editor v1 does
  not author them.

We never write a new AnimBase file. The client already has the monkey base.

## Extras library (tool + client)

No JavaFX. Copy or compile these sources into the client:

- `rs530anim.extras.SeqExtras`
- `rs530anim.extras.ExtrasStore`
- `rs530anim.anim.AnimFrame` / `AnimBase`
- `rs530anim.model.Rs2Buffer`

```text
gradlew.bat run --args="export 220 9220"
gradlew.bat run --args="import 9220"
```

In the viewer: extras seq id field, Export / Import. Import needs a cache seq already loaded so the existing AnimBase is available.

Client hook: `rs530anim.extras.ClientExtrasStub` (comment only). `SeqTypeList.get` / `AnimFrameset.get` check `extras/seq/<id>.json` first, then the live cache. Reuse `AnimBaseList.get(baseId)`.

### What we will not export

- A packed `main_file_cache.dat2` / `.idx`
- A new skeleton / base
- Relabelled `132.dat` (labels stay exactly as dumped)

---

## 3. Client classes we must copy (do not guess further)

Paste these from the **Amilious / RT4 fork you actually run**, not from
memory. Upstream references (same names on Pazaz/RT4-Client):

| class | why |
|-------|-----|
| `rt4.Buffer` | `g1`, `g2`, `g1b`, `gsmart` — every decode path |
| `rt4.Model` | model decode; `vertexLabel` / packed vertex groups; `createLabelGroups()` (`vertexGroups[label] = int[]` of vertex indices) |
| `rt4.SoftwareModel` | the concrete `animate` / apply-frame implementation used in SD. This is the math preview must reuse. |
| `rt4.AnimBase` | `types[]`, `bones[][]` (label lists per group), `parts[]`, `shadow[]` |
| `rt4.AnimFrame` | frame decode (`indices`, `x/y/z`, `flags`, `prevOriginIndices`) |
| `rt4.AnimFrameset` | how a frame file's first u16 selects the base |
| `rt4.SeqType` | opcode 1 frames+delays, opcode 9 looptype, opcode 5 priority |
| `rt4.SeqTypeList` | only as a reference for where extras should hook |

Optional later, only if preview needs HD parity: `rt4.GlModel` apply-frame.

**Do not copy** `client`, `Js5`, `JagString`, the rest of the runtime.

Please paste (or drop into `vendor/rt4/`) before the next slice:

1. `Model.java` — decode + label-group build + any `animate`/`apply` on the abstract class
2. `SoftwareModel.java` — the method that walks `AnimFrame` groups and rotates/translates verts
3. `AnimFrame.java`, `AnimBase.java`, `AnimFrameset.java`
4. `SeqType.java` (decode + fields is enough)
5. `Buffer.java`

Until those land, this repo will not encode frames and will not claim client-identical animate math.

### How 530 playback actually works (reference, not invented)

```text
SeqType.frames[i]     → packed (framesetId << 16) | frameIndex     [cache]
                       → extras/frames/<seqId>/<i>.dat             [ours]
AnimFrame             → list of used AnimBase slots + signed dx/dy/dz
AnimBase.types[slot]  → 0 origin, 1 translate, 2 rotate, 3 scale, 5 alpha, 7 color
AnimBase.bones[slot]  → label ids
Model.vertexGroups[label] → vertices that carry that vskin
SoftwareModel.apply   → origin first, then rotate/translate/scale those verts
```

Rotate is the classic 530 sine table pair around the origin the previous type-0
group established (`prevOriginIndices` on the frame). Preview in a later slice
will call the copied apply method, not a rewrite.

---

## 4. Loading extras later in Amilious / RT4

Do this in the **client repo**, not here.

Suggested hook points:

**Frames** — next to `AnimFrameset` construction:

```text
// pseudocode
Path extra = extrasRoot.resolve("frames/" + seqId + "/" + frameIndex + ".dat");
if (Files.exists(extra)) {
    byte[] bytes = Files.readAllBytes(extra);
    int baseId = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    AnimBase base = AnimBase.get(baseId);   // existing cache base
    return new AnimFrame(bytes, base);
}
// else existing js5 path
```

**Sequences** — next to `SeqTypeList.get(id)`:

```text
Path extra = extrasRoot.resolve("seq/" + id + ".json");
if (Files.exists(extra)) {
    // fill SeqType.frames[i] with a sentinel that your AnimFrameset
    // loader already routes to extras/frames/<id>/<i>.dat
    // fill SeqType.frameDelay from json.delays
    // looptype = json.loop ? 2 : 0
}
```

Keep extras root configurable (system property or a file next to the cache
folder). Never write into `main_file_cache.dat2`.

Server side: give the NPC/player `animate(90000)` the same way you already
play 220 / 223 / 1392. No cache pack required if the client extras hook is in.

---

## Constraints this project will not violate

- Revision 530 legacy labels, not OSRS 2024 skeletal.
- Labels on model 132 stay as dumped so stock anims still bind.
- Windows. IntelliJ / Rider. Kotlin.
- One compiling slice per message.
