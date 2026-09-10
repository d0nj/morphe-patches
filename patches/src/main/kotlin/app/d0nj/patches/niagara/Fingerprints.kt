package app.d0nj.patches.niagara

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

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
