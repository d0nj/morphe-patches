package app.d0nj.patches.firefox

import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.d0nj.patches.shared.copyBundledResources
import app.d0nj.patches.shared.elementByName
import app.morphe.patcher.patch.resourcePatch

private const val BUNDLED_RESOURCE_DIRECTORY = "firefox-nightly"

private val nightlyResourceFiles = listOf(
    "drawable-hdpi/ic_logo_wordmark_normal.webp",
    "drawable-hdpi/ic_logo_wordmark_private.webp",
    "drawable-mdpi/ic_logo_wordmark_normal.webp",
    "drawable-mdpi/ic_logo_wordmark_private.webp",
    "drawable-nodpi/ic_wordmark_logo.webp",
    "drawable-nodpi/ic_wordmark_text_normal.webp",
    "drawable-nodpi/ic_wordmark_text_private.webp",
    "drawable-xhdpi/ic_logo_wordmark_normal.webp",
    "drawable-xhdpi/ic_logo_wordmark_private.webp",
    "drawable-xxhdpi/ic_logo_wordmark_normal.webp",
    "drawable-xxhdpi/ic_logo_wordmark_private.webp",
    "drawable-xxxhdpi/ic_logo_wordmark_normal.webp",
    "drawable-xxxhdpi/ic_logo_wordmark_private.webp",
    "drawable/\$ic_firefox__0.xml",
    "drawable/\$ic_firefox__1.xml",
    "drawable/\$ic_firefox__2.xml",
    "drawable/\$ic_firefox__3.xml",
    "drawable/\$ic_firefox__4.xml",
    "drawable/\$ic_firefox__5.xml",
    "drawable/\$ic_firefox__6.xml",
    "drawable/\$ic_firefox__7.xml",
    "drawable/\$ic_firefox__8.xml",
    "drawable/\$ic_firefox__9.xml",
    "drawable/\$ic_firefox__10.xml",
    "drawable/\$ic_firefox__11.xml",
    "drawable/ic_firefox.xml",
    "drawable/\$ic_launcher_foreground__0.xml",
    "drawable/\$ic_launcher_foreground__1.xml",
    "drawable/\$ic_launcher_foreground__2.xml",
    "drawable/\$ic_launcher_foreground__3.xml",
    "drawable/\$ic_launcher_foreground__4.xml",
    "drawable/\$ic_launcher_foreground__5.xml",
    "drawable/\$ic_launcher_foreground__6.xml",
    "drawable/\$ic_launcher_foreground__7.xml",
    "drawable/\$ic_launcher_foreground__8.xml",
    "drawable/\$ic_launcher_foreground__9.xml",
    "drawable/\$ic_launcher_foreground__10.xml",
    "drawable/\$ic_launcher_foreground__11.xml",
    "drawable/ic_launcher_foreground.xml",
)

@Suppress("unused")
val nightlyBrandingPatch = resourcePatch(
    name = "Firefox Nightly branding",
    description = "Replaces the Firefox launcher icon and in-app wordmark with the Nightly " +
        "ones and renames the app to Firefox Nightly.",
    default = true,
) {
    compatibleWith(
        Compatibility(
            packageName = "org.mozilla.firefox",
            name = "Firefox",
            appIconColor = 0x00DDFF,
            targets = listOf(AppTarget(version = null)),
        ),
    )

    execute {
        copyBundledResources(BUNDLED_RESOURCE_DIRECTORY, nightlyResourceFiles)

        document("res/values/colors.xml").use { colors ->
            colors.elementByName("ic_launcher_background").textContent = NIGHTLY_LAUNCHER_BACKGROUND
        }

        document("res/values/strings.xml").use { strings ->
            strings.elementByName("app_name").textContent = NIGHTLY_APP_NAME
        }
    }
}

private const val NIGHTLY_LAUNCHER_BACKGROUND = "#220033"
private const val NIGHTLY_APP_NAME = "Firefox Nightly"
