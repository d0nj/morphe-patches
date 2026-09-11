package app.d0nj.patches.novelreader

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

private const val PIPER_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/en/"

private const val ESPEAK_BASE = "https://raw.githubusercontent.com/d0nj/morphe-patches/main/espeak-ng-data/"

private val piperModelPaths = listOf(
    "en_US/amy/low/en_US-amy-low.onnx",
    "en_US/amy/medium/en_US-amy-medium.onnx",
    "en_US/arctic/medium/en_US-arctic-medium.onnx",
    "en_US/bryce/medium/en_US-bryce-medium.onnx",
    "en_US/danny/low/en_US-danny-low.onnx",
    "en_US/hfc_female/medium/en_US-hfc_female-medium.onnx",
    "en_US/hfc_male/medium/en_US-hfc_male-medium.onnx",
    "en_US/joe/medium/en_US-joe-medium.onnx",
    "en_US/john/medium/en_US-john-medium.onnx",
    "en_US/kathleen/low/en_US-kathleen-low.onnx",
    "en_US/kristin/medium/en_US-kristin-medium.onnx",
    "en_US/kusal/medium/en_US-kusal-medium.onnx",
    "en_US/l2arctic/medium/en_US-l2arctic-medium.onnx",
    "en_US/lessac/high/en_US-lessac-high.onnx",
    "en_US/lessac/low/en_US-lessac-low.onnx",
    "en_US/lessac/medium/en_US-lessac-medium.onnx",
    "en_US/libritts/high/en_US-libritts-high.onnx",
    "en_US/libritts_r/medium/en_US-libritts_r-medium.onnx",
    "en_US/ljspeech/high/en_US-ljspeech-high.onnx",
    "en_US/ljspeech/medium/en_US-ljspeech-medium.onnx",
    "en_US/norman/medium/en_US-norman-medium.onnx",
    "en_US/reza_ibrahim/medium/en_US-reza_ibrahim-medium.onnx",
    "en_US/ryan/high/en_US-ryan-high.onnx",
    "en_US/ryan/low/en_US-ryan-low.onnx",
    "en_US/ryan/medium/en_US-ryan-medium.onnx",
    "en_US/sam/medium/en_US-sam-medium.onnx",
    "en_GB/alan/low/en_GB-alan-low.onnx",
    "en_GB/alan/medium/en_GB-alan-medium.onnx",
    "en_GB/alba/medium/en_GB-alba-medium.onnx",
    "en_GB/aru/medium/en_GB-aru-medium.onnx",
    "en_GB/cori/high/en_GB-cori-high.onnx",
    "en_GB/cori/medium/en_GB-cori-medium.onnx",
    "en_GB/jenny_dioco/medium/en_GB-jenny_dioco-medium.onnx",
    "en_GB/northern_english_male/medium/en_GB-northern_english_male-medium.onnx",
    "en_GB/semaine/medium/en_GB-semaine-medium.onnx",
    "en_GB/southern_english_female/low/en_GB-southern_english_female-low.onnx",
    "en_GB/vctk/medium/en_GB-vctk-medium.onnx",
)

private val espeakFiles = listOf(
    "en_dict",
    "lang/gmw/en",
    "lang/gmw/en-029",
    "lang/gmw/en-GB-scotland",
    "lang/gmw/en-GB-x-gbclan",
    "lang/gmw/en-GB-x-gbcwmd",
    "lang/gmw/en-GB-x-rp",
    "lang/gmw/en-US",
    "lang/gmw/en-US-nyc",
)

private fun voiceDisplayName(model: String): String {
    val region = if (model.startsWith("en_US")) "US" else "UK"
    val quality = model.substringAfterLast('-')
    val voice = model.substringBeforeLast('-').substringAfter('-')
    val pretty = voice.split('_').joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
    return "$pretty ($region, $quality)"
}

private fun buildCatalogJson(): String = buildString {
    append("{\"version\":1,\"models\":[")
    piperModelPaths.joinTo(this, ",") { path ->
        val model = path.substringAfterLast('/')
        val modelId = model.removeSuffix(".onnx")
        val lang = if (model.startsWith("en_US")) "en-US" else "en-GB"
        "{\"id\":\"$modelId\",\"languageCode\":\"$lang\",\"displayName\":\"${voiceDisplayName(modelId)}\"," +
            "\"fileName\":\"$model\",\"onnxUrl\":\"$PIPER_BASE$path\"," +
            "\"jsonUrl\":\"$PIPER_BASE$path.json\",\"demoUrl\":\"\"}"
    }
    append("]}")
}

private fun buildEspeakBlock(downloaderRef: MethodReference): String = buildString {
    append("new-instance v0, Ljava/io/File;\n")
    append("invoke-virtual {p0}, Landroid/content/Context;->getFilesDir()Ljava/io/File;\n")
    append("move-result-object v1\n")
    append("const-string v2, \"tts_voices/espeak-ng-data\"\n")
    append("invoke-direct {v0, v1, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V\n")
    espeakFiles.forEachIndexed { index, relPath ->
        append("new-instance v1, Ljava/io/File;\n")
        append("const-string v2, \"$relPath\"\n")
        append("invoke-direct {v1, v0, v2}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V\n")
        append("invoke-virtual {v1}, Ljava/io/File;->exists()Z\n")
        append("move-result v3\n")
        append("if-nez v3, :espeak_skip_$index\n")
        append("invoke-virtual {v1}, Ljava/io/File;->getParentFile()Ljava/io/File;\n")
        append("move-result-object v3\n")
        append("if-eqz v3, :espeak_skip_$index\n")
        append("invoke-virtual {v3}, Ljava/io/File;->mkdirs()Z\n")
        append("const-string v4, \"$ESPEAK_BASE$relPath\"\n")
        append("invoke-static {p0, v1, v4}, ${downloaderRef.definingClass}->${downloaderRef.name}" +
            "(${downloaderRef.parameterTypes.joinToString("")})${downloaderRef.returnType}\n")
        append(":espeak_skip_$index\n")
    }
    append("nop\n")
}

@Suppress("unused")
val addEnglishTtsVoicesPatch = bytecodePatch(
    name = "Add English TTS voices",
    description = "Adds English piper voices to AI Audio Novel Reader by injecting a voice catalog " +
        "for the English novel language mode (37 voices from rhasspy/piper-voices, downloaded on " +
        "first use) and fetching the missing English espeak-ng phonemization data (~170 KB, one " +
        "time). Vietnamese voices are not affected.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            packageName = "com.thien.novelreader",
            name = "AI Audio Novel Reader",
            appIconColor = 0x006200EE,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    execute {
        val catalogJson = buildCatalogJson()

        val catalogMethod = TtsCatalogLoadFingerprint.method
        val catalogInstructions = catalogMethod.implementation!!.instructions.toList()

        val anchorIndex = catalogInstructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference is StringReference &&
                ((instruction as ReferenceInstruction).reference as StringReference).string == " TTS config available yet"
        }
        if (anchorIndex < 0) {
            throw PatchException("Could not find the TTS catalog log anchor")
        }

        val emptyStringIndex = catalogInstructions.drop(anchorIndex + 1).indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_STRING &&
                ((instruction as ReferenceInstruction).reference as StringReference).string.isEmpty()
        }.let { if (it == -1) -1 else anchorIndex + 1 + it }
        if (emptyStringIndex < 0) {
            throw PatchException("Could not find the empty TTS config fallback string")
        }

        val emptyStringInstruction = catalogInstructions[emptyStringIndex] as OneRegisterInstruction
        catalogMethod.implementation!!.removeInstruction(emptyStringIndex)
        catalogMethod.implementation!!.addInstruction(
            emptyStringIndex,
            BuilderInstruction21c(
                Opcode.CONST_STRING,
                emptyStringInstruction.registerA,
                ImmutableStringReference(catalogJson),
            ),
        )

        val downloadMethod = TtsVoiceDownloadFingerprint.method
        val downloadInstructions = downloadMethod.implementation!!.instructions.toList()

        val downloaderRef = downloadInstructions
            .asSequence()
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            .firstOrNull {
                it.returnType == "Z" &&
                    it.parameterTypes == listOf("Landroid/content/Context;", "Ljava/io/File;", "Ljava/lang/String;")
            }
            ?: throw PatchException("Could not find the TTS voice downloader method")

        val localRegisters = downloadMethod.implementation!!.registerCount - downloadMethod.parameters.size
        if (localRegisters < 5) {
            throw PatchException(
                "The TTS voice download method has too few local registers: $localRegisters",
            )
        }

        val insertIndex = downloadInstructions.size - 1
        downloadMethod.addInstructionsWithLabels(insertIndex, buildEspeakBlock(downloaderRef))
    }
}
