package app.d0nj.patches.novelreader

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31i
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference

private const val PIPER_BASE = "https://huggingface.co/csukuangfj/"

private const val ESPEAK_BASE = "https://raw.githubusercontent.com/d0nj/morphe-patches/main/espeak-ng-data/"

private val piperModelIds = listOf(
    "en_US-amy-low",
    "en_US-amy-medium",
    "en_US-arctic-medium",
    "en_US-bryce-medium",
    "en_US-danny-low",
    "en_US-hfc_female-medium",
    "en_US-hfc_male-medium",
    "en_US-joe-medium",
    "en_US-john-medium",
    "en_US-kathleen-low",
    "en_US-kristin-medium",
    "en_US-kusal-medium",
    "en_US-l2arctic-medium",
    "en_US-lessac-high",
    "en_US-lessac-low",
    "en_US-lessac-medium",
    "en_US-libritts-high",
    "en_US-libritts_r-medium",
    "en_US-ljspeech-high",
    "en_US-ljspeech-medium",
    "en_US-norman-medium",
    "en_US-ryan-high",
    "en_US-ryan-low",
    "en_US-ryan-medium",
    "en_GB-alan-low",
    "en_GB-alan-medium",
    "en_GB-alba-medium",
    "en_GB-aru-medium",
    "en_GB-cori-high",
    "en_GB-cori-medium",
    "en_GB-jenny_dioco-medium",
    "en_GB-northern_english_male-medium",
    "en_GB-semaine-medium",
    "en_GB-southern_english_female-low",
    "en_GB-vctk-medium",
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
    piperModelIds.joinTo(this, ",") { modelId ->
        catalogEntry(modelId, null)
    }
    append("]}")
}

private fun buildDupeEntriesJson(): String =
    piperModelIds.joinToString(",") { modelId ->
        catalogEntry(modelId, "vi")
    }

private fun catalogEntry(modelId: String, languageOverride: String?): String {
    val model = "$modelId.onnx"
    val lang = languageOverride
        ?: if (model.startsWith("en_US")) "en-US" else "en-GB"
    val id = if (languageOverride == null) modelId else "$modelId-vi"
    val fileUrl = "${PIPER_BASE}vits-piper-$modelId/resolve/main/$model"
    return "{\"id\":\"$id\",\"languageCode\":\"$lang\",\"displayName\":\"${voiceDisplayName(modelId)}\"," +
        "\"fileName\":\"$model\",\"onnxUrl\":\"$fileUrl\"," +
        "\"jsonUrl\":\"$fileUrl.json\",\"demoUrl\":\"\"}"
}

private fun buildEspeakBlock(downloaderRef: MethodReference): String = buildString {
    append("new-instance v0, Ljava/io/File;\n")
    append("invoke-virtual {p1}, Landroid/content/Context;->getFilesDir()Ljava/io/File;\n")
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
        append("invoke-static {p1, v1, v4}, ${downloaderRef.definingClass}->${downloaderRef.name}" +
            "(${downloaderRef.parameterTypes.joinToString("")})${downloaderRef.returnType}\n")
        append(":espeak_skip_$index\n")
    }
    append("nop\n")
}

@Suppress("unused")
val addEnglishTtsVoicesPatch = bytecodePatch(
    name = "Add English TTS voices",
    description = "Adds English piper voices to AI Audio Novel Reader by injecting a voice catalog " +
        "for the English novel language mode (35 voices repackaged for sherpa-onnx, downloaded on " +
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

        val downloaderRef = catalogInstructions
            .asSequence()
            .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
            .firstOrNull {
                it.returnType == "Z" &&
                    it.parameterTypes == listOf("Landroid/content/Context;", "Ljava/io/File;", "Ljava/lang/String;")
            }
            ?: run {
                TtsCatalogLoadFingerprint.originalClassDef.methods
                    .asSequence()
                    .flatMap { it.implementation?.instructions?.toList() ?: emptyList() }
                    .mapNotNull { (it as? ReferenceInstruction)?.reference as? MethodReference }
                    .firstOrNull {
                        it.returnType == "Z" && it.parameterTypes == listOf(
                            "Landroid/content/Context;",
                            "Ljava/io/File;",
                            "Ljava/lang/String;",
                        )
                    }
            }
            ?: throw PatchException("Could not find the TTS voice downloader method")

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

        val localRegisters = catalogMethod.implementation!!.registerCount - catalogMethod.parameters.size
        if (localRegisters < 5) {
            throw PatchException(
                "The TTS catalog load method has too few local registers: $localRegisters",
            )
        }

        val isEmptyCheckIndex = catalogInstructions.drop(emptyStringIndex + 1).indexOfFirst { instruction ->
            if (instruction.opcode != Opcode.INVOKE_STATIC) return@indexOfFirst false
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@indexOfFirst false
            reference.returnType == "Z" &&
                reference.parameterTypes == listOf("Ljava/lang/CharSequence;")
        }.let { if (it == -1) -1 else emptyStringIndex + 1 + it }
        if (isEmptyCheckIndex < 0) {
            throw PatchException("Could not find the TTS config empty check")
        }

        val dupeEntries = buildDupeEntriesJson()
        val searchPatterns = listOf("\"models\":[", "\"models\": [", "\"models\" : [")
        val replaceProto = "\"models\": [$dupeEntries, "
        val stringReplace = ImmutableMethodReference(
            "Ljava/lang/String;",
            "replace",
            listOf("Ljava/lang/CharSequence;", "Ljava/lang/CharSequence;"),
            "Ljava/lang/String;",
        )
        var spliceIndex = isEmptyCheckIndex
        for (pattern in searchPatterns) {
            catalogMethod.implementation!!.addInstruction(
                spliceIndex++,
                BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    3,
                    ImmutableStringReference(pattern),
                ),
            )
            catalogMethod.implementation!!.addInstruction(
                spliceIndex++,
                BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    5,
                    ImmutableStringReference(replaceProto),
                ),
            )
            catalogMethod.implementation!!.addInstruction(
                spliceIndex++,
                BuilderInstruction35c(
                    Opcode.INVOKE_VIRTUAL,
                    3,
                    2,
                    3,
                    5,
                    0,
                    0,
                    stringReplace,
                ),
            )
            catalogMethod.implementation!!.addInstruction(
                spliceIndex++,
                BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 2),
            )
        }
        catalogMethod.implementation!!.addInstruction(
            spliceIndex,
            BuilderInstruction11n(Opcode.CONST_4, 3, 0),
        )

        catalogMethod.addInstructionsWithLabels(0, buildEspeakBlock(downloaderRef))

        val clientField = classDefBy(downloaderRef.definingClass).methods
            .asSequence()
            .flatMap { it.implementation?.instructions?.toList() ?: emptyList() }
            .mapNotNull { instruction ->
                if (instruction.opcode != Opcode.SPUT_OBJECT) return@mapNotNull null
                ((instruction as ReferenceInstruction).reference as? FieldReference)
                    ?.takeIf { it.definingClass == downloaderRef.definingClass }
            }
            .firstOrNull()
            ?: throw PatchException("Could not find the HTTP client field in the TTS downloader")
        val clientType = clientField.type
        val clientInit = classDefBy(clientType).methods
            .singleOrNull { it.name == "<init>" && it.parameterTypes.size == 1 }
            ?: throw PatchException("Could not find the HTTP client constructor")
        val builderType = clientInit.parameterTypes[0] as String

        val builderInit = classDefBy(builderType).methods
            .singleOrNull { it.name == "<init>" && it.parameterTypes.isEmpty() }
            ?: throw PatchException("Could not find the HTTP builder constructor")
        val mutableBuilderInit = mutableClassDefBy(builderType).methods
            .single { it.name == "<init>" && it.parameterTypes == builderInit.parameterTypes }
        val builderInitInstructions = builderInit.implementation!!.instructions.toList()
        val timeoutFields = mutableListOf<String>()
        var timeoutRegister = -1
        for (instruction in builderInitInstructions) {
            if (instruction.opcode == Opcode.IPUT) {
                val field = ((instruction as ReferenceInstruction).reference as? FieldReference)
                    ?.takeIf { it.definingClass == builderType && it.type == "I" }
                if (field != null &&
                    (instruction as TwoRegisterInstruction).registerA == timeoutRegister
                ) {
                    timeoutFields.add(field.name)
                }
                continue
            }
            if (instruction is OneRegisterInstruction) {
                if (instruction.opcode == Opcode.CONST_16 &&
                    (instruction as NarrowLiteralInstruction).narrowLiteral == 10000
                ) {
                    timeoutRegister = instruction.registerA
                } else if (instruction.registerA == timeoutRegister) {
                    timeoutRegister = -1
                }
                continue
            }
            if (instruction is TwoRegisterInstruction) {
                when (instruction.opcode) {
                    Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT, Opcode.IGET_BOOLEAN,
                    Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT, Opcode.AGET,
                    Opcode.AGET_WIDE, Opcode.AGET_OBJECT, Opcode.AGET_BOOLEAN, Opcode.AGET_BYTE,
                    Opcode.AGET_CHAR, Opcode.AGET_SHORT,
                    -> if (instruction.registerA == timeoutRegister) timeoutRegister = -1
                    else -> {}
                }
            }
        }
        if (timeoutFields.size != 3) {
            throw PatchException(
                "Expected 3 HTTP timeout fields, found ${timeoutFields.size}: $timeoutFields",
            )
        }

        val timeoutConstIndex = builderInitInstructions.indexOfFirst { instruction ->
            instruction.opcode == Opcode.CONST_16 &&
                (instruction as NarrowLiteralInstruction).narrowLiteral == 10000
        }
        val timeoutConst = builderInitInstructions[timeoutConstIndex] as OneRegisterInstruction
        mutableBuilderInit.implementation!!.replaceInstruction(
            timeoutConstIndex,
            BuilderInstruction31i(Opcode.CONST, timeoutConst.registerA, HTTP_TIMEOUT_MS),
        )
    }
}

private const val HTTP_TIMEOUT_MS = 600000
