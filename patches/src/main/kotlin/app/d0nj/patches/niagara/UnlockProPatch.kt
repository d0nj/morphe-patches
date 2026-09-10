package app.d0nj.patches.niagara

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.bytecodePatch
import app.d0nj.patches.shared.clearBody

@Suppress("unused")
val unlockProPatch = bytecodePatch(
    name = "Unlock Pro",
    description = "Makes the entitlement holder always report Pro in Niagara Launcher. " +
        "Unlocks all Pro features and bypasses the 7-day trial prompt. " +
        "Server-backed features (account sync, Stripe checkout) are not affected.",
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

    execute {
        val classType = EntitlementTripleFingerprint.originalClassDef.type
        val booleanFields = EntitlementTripleFingerprint.originalClassDef.fields
            .filter { it.type == "Z" }
        val fieldWrites = booleanFields.joinToString("\n") { field ->
            "iput-boolean p1, p0, $classType->${field.name}:Z"
        }
        EntitlementTripleFingerprint.method.apply {
            clearBody()
            addInstructions(
                0,
                "invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n" +
                    "const/4 p1, 0x1\n" +
                    fieldWrites +
                    "\nreturn-void",
            )
        }
    }
}
