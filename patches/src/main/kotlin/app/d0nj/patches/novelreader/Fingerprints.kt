package app.d0nj.patches.novelreader

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

object IsPremiumFingerprint : Fingerprint(
    definingClass = "Lcom/novelreader/domain/premium/PremiumData;",
    name = "isPremium",
    returnType = "Z",
    parameters = emptyList(),
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
)

object TtsCatalogLoadFingerprint : Fingerprint(
    strings = listOf(" TTS config available yet"),
)
