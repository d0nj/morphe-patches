package app.d0nj.patches.niagara

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

/**
 * The entitlement data class is rebuilt by R8 with new names every release, but its
 * shape is invariant: one obfuscated class with exactly three boolean fields, a
 * public (ZZZ)V constructor and an equals method. Verified unique in 1.16.23/27/28.
 */
object EntitlementTripleFingerprint : Fingerprint(
    definingClass = "Lb",
    name = "<init>",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR),
    returnType = "V",
    parameters = listOf("Z", "Z", "Z"),
    custom = fun(_: Method, classDef: ClassDef) =
        classDef.fields.count { it.type == "Z" } == 3 &&
            classDef.fields.count() == 3 &&
            classDef.methods.any { it.name == "equals" },
)

/**
 * Work method of the telemetry workers: a single obfuscated continuation parameter
 * returning Object (Kotlin suspend doWork). The method name is renamed per build,
 * so only the shape is matched. Verified exactly one match per worker class.
 */
object UsageReportUploadWorkFingerprint : Fingerprint(
    definingClass = "Lbitpit/launcher/usage/report/UsageReportUploadWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
)

object RetentionEventsWorkFingerprint : Fingerprint(
    definingClass = "Lbitpit/launcher/analytics/RetentionEventsWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
)

object GdprConsentSyncWorkFingerprint : Fingerprint(
    definingClass = "Lbitpit/launcher/analytics/consent/GdprConsentSyncWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
)

object SingularFirstSessionWorkFingerprint : Fingerprint(
    definingClass = "Lbitpit/launcher/analytics/singular/SingularFirstSessionReportWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
)

object SingularSessionWorkFingerprint : Fingerprint(
    definingClass = "Lbitpit/launcher/analytics/singular/SingularSessionReportWorker;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
)

/**
 * Shared suspend POST helper of the attribution client. Its exact signature
 * (String path, request DTO, KSerializer, continuation) is unique per build.
 */
object AttributionPostFingerprint : Fingerprint(
    definingClass = "Lb",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lb",
        "Lkotlinx/serialization/KSerializer;",
        "Lb",
    ),
)

/**
 * Runtime method that flips Firebase Analytics collection on. Shape: public final
 * (Z)V in an obfuscated class that both reads a FirebaseAnalytics-typed field and
 * submits a boxed Boolean (TRUE before patching, FALSE in already-patched trees).
 * Verified exactly one match per build in 1.16.23/27/28.
 */
object FirebaseCollectionToggleFingerprint : Fingerprint(
    definingClass = "Lb",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    custom = fun(method: Method, _: ClassDef): Boolean {
        val instructions = method.implementation?.instructions ?: return false
        var boxedBoolean = false
        var firebaseAnalytics = false
        for (instruction in instructions) {
            val reference = (instruction as? ReferenceInstruction)?.reference ?: continue
            if (reference !is FieldReference) continue
            if (reference.definingClass == "Ljava/lang/Boolean;" &&
                (reference.name == "TRUE" || reference.name == "FALSE")
            ) {
                boxedBoolean = true
            }
            if (reference.definingClass == "Lcom/google/firebase/analytics/FirebaseAnalytics;") {
                firebaseAnalytics = true
            }
        }
        return boxedBoolean && firebaseAnalytics
    },
)
