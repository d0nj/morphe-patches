package app.d0nj.patches.niagara

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val ADAPTER = "Lapp/d0nj/extension/niagara/OpenMeteoWeather;"
private const val WEATHER_REPOSITORY = "Lb/ajHlrBTP7J0VEvJDPHhJQ19Pqu5;"

@Suppress("unused")
val openMeteoWeatherPatch = bytecodePatch(
    name = "Use Open-Meteo for weather",
    description = "Replaces Niagara's account-gated weather backend with the keyless Open-Meteo API. " +
        "Weather no longer depends on a Niagara account session, which is what made the " +
        "at-a-glance tile fail with NoWeatherDataException 'st missing' when nobody was signed in. " +
        "The response is re-encoded into Niagara's existing forecast format, so the cache, refresh " +
        "timer and tile rendering are unchanged.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            packageName = "bitpit.launcher",
            name = "Niagara Launcher",
            appIconColor = 0x1E88E5,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    extendWith("extensions/extension.mpe")

    execute {
        val fetch = weatherFetchCoroutine()
            ?: throw PatchException("Could not locate the Niagara weather fetch coroutine")

        val mutableClass = mutableClassDefBy(fetch.definingClass)
        val method = mutableClass.methods.first {
            it.name == fetch.name && it.parameterTypes == fetch.parameterTypes
        }

        redirectFetchToAdapter(method)
    }
}

/** Identified by reading the session, storing the forecast, and the "weather2" literal. */
private fun BytecodePatchContext.weatherFetchCoroutine(): Method? {
    var match: Method? = null
    classDefForEach { classDef ->
        if (match != null) return@classDefForEach
        if (!classDef.type.startsWith("Lb/")) return@classDefForEach
        for (method in classDef.methods) {
            if (match != null) break
            val body = method.implementation?.instructions ?: continue
            val calls = body.filterIsInstance<ReferenceInstruction>()
                .mapNotNull { it.reference as? MethodReference }
            val strings = body.filterIsInstance<ReferenceInstruction>()
                .mapNotNull { (it.reference as? com.android.tools.smali.dexlib2.iface.reference.StringReference)?.string }

            val readsSession = calls.any {
                it.name == "JtucrXqNywWZ" && it.parameterTypes.isEmpty() &&
                    it.returnType == "Lb/XESKmV6VBJGMTGgq1BSLSK1SU6eV6;"
            }
            val storesForecast = calls.any {
                it.name == "CCeyZfVn1rkb5" &&
                    it.parameterTypes == listOf("Ljava/lang/String;", "J") &&
                    it.definingClass == "Lb/ajHlrBTP7J0VEvJDPHhJQ19Pqu5;"
            }
            val weatherEndpoint = strings.contains("weather2")

            if (readsSession && storesForecast && weatherEndpoint) {
                match = method
                break
            }
        }
    }
    return match
}

/**
 * Replaces the weather fetch's fall-through body with an adapter call that
 * feeds Niagara's own parse-and-store method.
 */
private fun BytecodePatchContext.redirectFetchToAdapter(method: MutableMethod) {
    val repositoryType = WEATHER_REPOSITORY
    val repositoryField = methodRepository(method)
        ?: throw PatchException("Weather repository field not found on ${method.definingClass}")

    val repositoryDef = classDefByOrNull(repositoryType)
        ?: throw PatchException("Weather repository class not found")
    val coordinatesField = repositoryDef.fields
        .firstOrNull { field ->
            field.type != "F" && classDefByOrNull(field.type)?.fields?.count { it.type == "F" } == 2
        }
        ?: throw PatchException("Weather coordinates field not found on $repositoryType")

    val coordinatesType = coordinatesField.type
    val coordinatesDef = classDefByOrNull(coordinatesType)
        ?: throw PatchException("Weather coordinates class definition not found")
    val floats = coordinatesDef.fields.filter { it.type == "F" }
    if (floats.size < 2) throw PatchException("Weather coordinates lacks two floats")

    val store = repositoryDef.methods.firstOrNull {
        it.parameterTypes == listOf("Ljava/lang/String;", "J") && it.returnType.startsWith("L")
    } ?: throw PatchException("Weather store method not found")

    val coordinatesCapture = coordinatesCapture(method, coordinatesType)
        ?: throw PatchException("Weather coordinates capture field not found on ${method.definingClass}")

    // Weather is the packed-switch fall-through (index 0xf); its body throws
    // "st missing" for accountless users before any network call, so the entry
    // right after the switch is the only seam that covers them.
    // Injected code must stay branch-free (VerifyError) and must not throw
    // (fatal crash); field refs must use the declared Object type.
    val switchIndex = method.instructions.indexOfFirst { it.opcode == Opcode.PACKED_SWITCH }
    if (switchIndex < 0) throw PatchException("Weather dispatch packed-switch not found")

    method.addInstructions(
        switchIndex + 1,
        """
            iget-object v0, v7, ${method.definingClass}->${repositoryField.name}:${repositoryField.type}
            check-cast v0, $repositoryType
            iget-object v1, v7, ${method.definingClass}->${coordinatesCapture.name}:${coordinatesCapture.type}
            invoke-static {v1}, $ADAPTER->forecast(Ljava/lang/Object;)Ljava/lang/String;
            move-result-object v1
            invoke-static {v1}, $ADAPTER->requireForecast(Ljava/lang/String;)Ljava/lang/String;
            move-result-object v1
            invoke-static {}, Ljava/lang/System;->currentTimeMillis()J
            move-result-wide v3
            invoke-virtual {v0, v1, v3, v4}, $repositoryType->${store.name}(Ljava/lang/String;J)${store.returnType}
            move-result-object v0
            return-object v0
        """.trimIndent(),
    )
}

/** Coordinates capture: an Object field immediately check-cast to the coordinates type. */
private fun BytecodePatchContext.coordinatesCapture(
    method: MutableMethod,
    coordinatesType: String,
): FieldReference? {
    val instructions = method.implementation?.instructions?.toList() ?: return null
    for (index in instructions.indices) {
        val field = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference ?: continue
        if (field.definingClass != method.definingClass) continue
        if (field.type != "Ljava/lang/Object;") continue
        val cast = (instructions.getOrNull(index + 1) as? ReferenceInstruction)?.reference as? TypeReference
        if (cast?.type == coordinatesType) return field
    }
    return null
}

/** Repository capture: an Object field immediately check-cast to the repository type. */
private fun BytecodePatchContext.methodRepository(method: MutableMethod): FieldReference? {
    val instructions = method.implementation?.instructions?.toList() ?: return null
    for (index in instructions.indices) {
        val field = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference ?: continue
        if (field.definingClass != method.definingClass) continue
        if (field.type != "Ljava/lang/Object;") continue
        val cast = (instructions.getOrNull(index + 1) as? ReferenceInstruction)?.reference as? TypeReference
        if (cast?.type == WEATHER_REPOSITORY) return field
    }
    return null
}
