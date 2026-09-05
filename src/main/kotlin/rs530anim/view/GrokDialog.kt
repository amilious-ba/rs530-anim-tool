package rs530anim.view

import javafx.application.Platform
import javafx.geometry.Insets
import javafx.scene.control.Alert
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog
import javafx.scene.control.Label
import javafx.scene.control.TextArea
import javafx.scene.control.TextInputDialog
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import rs530anim.ai.GrokAnimClient
import rs530anim.ai.GrokSettings
import rs530anim.ai.TrackPatch
import rs530anim.anim.AnimFrame

object GrokDialog {
    fun askKey(): String? {
        val existing = GrokSettings.apiKey()
        val dlg = TextInputDialog(existing ?: "")
        dlg.title = "Grok API key"
        dlg.headerText = "xAI key from console.x.ai. Saved in ~/.rs530-anim-tool/xai.key\nYou can also set XAI_API_KEY."
        dlg.contentText = "API key"
        val field = dlg.dialogPane.lookup(".text-field")
        return dlg.showAndWait().orElse(null)?.trim()?.takeIf { it.isNotEmpty() }?.also {
            GrokSettings.saveKey(it)
        }
    }

    fun promptAndGenerate(
        seqId: Int?,
        baseId: Int?,
        npcId: Int?,
        npcName: String?,
        modelIds: List<Int>,
        labels: List<Int>,
        selectedLabel: Int?,
        vertsOf: (Int) -> Int,
        frames: List<AnimFrame>,
        delays: IntArray,
        apply: (List<TrackPatch>) -> Unit,
    ) {
        var key = GrokSettings.apiKey()
        if (key.isNullOrBlank()) {
            key = askKey()
            if (key.isNullOrBlank()) return
        }
        val area = TextArea("Raise the selected group on frames 2-3, then settle.")
        area.prefRowCount = 6
        area.isWrapText = true
        val status = Label("Uses ${GrokSettings.model()} at api.x.ai")
        val generate = Button("Generate")
        val box = VBox(8.0, Label("Describe the motion"), area, generate, status)
        box.padding = Insets(10.0)
        VBox.setVgrow(area, Priority.ALWAYS)
        val dlg = Dialog<ButtonType>()
        dlg.title = "Generate with Grok"
        dlg.dialogPane.content = box
        dlg.dialogPane.buttonTypes.add(ButtonType.CLOSE)
        dlg.dialogPane.prefWidth = 520.0
        generate.setOnAction {
            val prompt = area.text.trim()
            if (prompt.isEmpty()) return@setOnAction
            generate.isDisable = true
            status.text = "calling Grok…"
            val who = GrokAnimClient.characterContext(npcId, npcName, modelIds, labels, vertsOf)
            val snapshot = GrokAnimClient.describeSequence(seqId, baseId, labels, selectedLabel, frames, delays)
            val user = "$who\nCurrent clip:\n$snapshot\n\nUser request:\n$prompt\n\nUse rotate and translate as needed. selected=${selectedLabel ?: labels.firstOrNull() ?: 0}."
            Thread {
                try {
                    val raw = GrokAnimClient.complete(key, GrokSettings.model(), GrokAnimClient.systemPrompt, user)
                    val patches = GrokAnimClient.sanitizePatches(GrokAnimClient.parsePatches(raw, frames.size))
                    Platform.runLater {
                        generate.isDisable = false
                        if (patches.isEmpty()) {
                            status.text = "Grok replied but no patches parsed. See console."
                            println("Grok raw:\n$raw")
                        } else {
                            status.text = "applied ${patches.size} patches"
                            apply(patches)
                        }
                    }
                } catch (e: Exception) {
                    Platform.runLater {
                        generate.isDisable = false
                        status.text = e.message ?: e.javaClass.simpleName
                        Alert(Alert.AlertType.ERROR, status.text, ButtonType.OK).showAndWait()
                    }
                    e.printStackTrace()
                }
            }.apply { name = "grok-generate"; isDaemon = true; start() }
        }
        dlg.showAndWait()
    }

    fun promptAndCreateNew(
        seqId: Int?,
        baseId: Int?,
        npcId: Int?,
        npcName: String?,
        modelIds: List<Int>,
        labels: List<Int>,
        vertsOf: (Int) -> Int,
        frames: List<AnimFrame>,
        delays: IntArray,
        apply: (patches: List<TrackPatch>, frameCount: Int) -> Unit,
    ) {
        var key = GrokSettings.apiKey()
        if (key.isNullOrBlank()) {
            key = askKey()
            if (key.isNullOrBlank()) return
        }
        val area = TextArea("Make a short new idle: slight body sway and head bob over 8 frames.")
        area.prefRowCount = 6
        area.isWrapText = true
        val status = Label("New clip call · full vskin grid · ${GrokSettings.model()}")
        val generate = Button("Create animation")
        val box = VBox(8.0, Label("Describe the new animation"), area, generate, status)
        box.padding = Insets(10.0)
        VBox.setVgrow(area, Priority.ALWAYS)
        val dlg = Dialog<ButtonType>()
        dlg.title = "New animation with Grok"
        dlg.dialogPane.content = box
        dlg.dialogPane.buttonTypes.add(ButtonType.CLOSE)
        dlg.dialogPane.prefWidth = 520.0
        generate.setOnAction {
            val prompt = area.text.trim()
            if (prompt.isEmpty()) return@setOnAction
            generate.isDisable = true
            status.text = "calling Grok…"
            val who = GrokAnimClient.characterContext(npcId, npcName, modelIds, labels, vertsOf)
            val snapshot = GrokAnimClient.describeFullGrid(seqId, baseId, labels, frames, delays)
            val user = "$who\nFull vskin table:\n$snapshot\n\nCreate a NEW animation for this NPC:\n$prompt"
            Thread {
                try {
                    val raw = GrokAnimClient.complete(key, GrokSettings.model(), GrokAnimClient.systemPromptNew, user)
                    val count = GrokAnimClient.parseFrameCount(raw, frames.size)
                    val patches = GrokAnimClient.sanitizePatches(GrokAnimClient.parsePatches(raw, count))
                    Platform.runLater {
                        generate.isDisable = false
                        if (patches.isEmpty()) {
                            status.text = "Grok replied but no patches parsed. See console."
                            println("Grok new raw:\n$raw")
                        } else {
                            status.text = "new clip: ${patches.size} patches, $count frames"
                            apply(patches, count)
                        }
                    }
                } catch (e: Exception) {
                    Platform.runLater {
                        generate.isDisable = false
                        status.text = e.message ?: e.javaClass.simpleName
                        Alert(Alert.AlertType.ERROR, status.text, ButtonType.OK).showAndWait()
                    }
                    e.printStackTrace()
                }
            }.apply { name = "grok-new-anim"; isDaemon = true; start() }
        }
        dlg.showAndWait()
    }
}
