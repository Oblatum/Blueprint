package dev.jahir.blueprint.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.drawable.Drawable
import dev.jahir.blueprint.R
import dev.jahir.blueprint.data.models.Icon
import dev.jahir.frames.extensions.resources.nextOrNull
import dev.jahir.frames.ui.activities.base.BaseLicenseCheckerActivity.Companion.PLAY_STORE_LINK_PREFIX
import dev.jahir.kuper.extensions.isAppInstalled
import org.xmlpull.v1.XmlPullParser

internal data class IconPackageResolution(
    val installedPackage: String?,
    val storePackage: String?
)

internal fun Context.findFirstUninstalledMappedPackageForIcon(icon: Icon): String? {
    return resolveIconPackages(icon).storePackage
}

internal fun Context.findFirstInstalledMappedPackageForIcon(icon: Icon): String? {
    return resolveIconPackages(icon).installedPackage
}

internal fun Context.resolveIconPackages(icon: Icon): IconPackageResolution {
    val drawableName = try {
        resources.getResourceEntryName(icon.resId)
    } catch (_: Exception) {
        null
    }
    if (drawableName.isNullOrBlank()) return IconPackageResolution(null, null)

    val pkgs = AppFilterPackagesCache.get(this)[drawableName].orEmpty()
    if (pkgs.isEmpty()) return IconPackageResolution(null, null)

    val installed = try {
        pkgs.firstOrNull { isAppInstalled(it) }
    } catch (_: Exception) {
        null
    }
    val store = if (installed == null) pkgs.firstOrNull() else null
    return IconPackageResolution(installed, store)
}

internal fun Context.tryLoadApplicationIcon(packageName: String): Drawable? {
    val pkg = packageName.trim()
    if (pkg.isBlank()) return null
    return try {
        packageManager.getApplicationIcon(pkg)
    } catch (_: Exception) {
        null
    }
}

internal fun Context.openAppInStore(packageName: String) {
    val pkg = packageName.trim()
    if (pkg.isBlank()) return

    try {
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(marketIntent)
        return
    } catch (_: Exception) {
    }

    try {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_LINK_PREFIX + pkg))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(webIntent)
    } catch (_: Exception) {
    }
}

private object AppFilterPackagesCache {

    private val lock = Any()

    @Volatile
    private var cachedPackageName: String? = null

    @Volatile
    private var cachedMap: Map<String, List<String>>? = null

    fun get(context: Context): Map<String, List<String>> {
        val pkg = context.packageName
        val current = cachedMap
        if (current != null && cachedPackageName == pkg) return current

        synchronized(lock) {
            val second = cachedMap
            if (second != null && cachedPackageName == pkg) return second

            val loaded = load(context)
            cachedPackageName = pkg
            cachedMap = loaded
            return loaded
        }
    }

    private fun load(context: Context): Map<String, List<String>> {
        val parser = try {
            context.resources.getXml(R.xml.appfilter)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        val result = HashMap<String, MutableSet<String>>()
        try {
            var event: Int? = parser.eventType
            while (event != null && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue(null, "drawable")?.trim().orEmpty()
                    if (drawable.isNotBlank()) {
                        val component = parser.getAttributeValue(null, "component")?.trim().orEmpty()
                        val pkg = component.toPackageName()
                        if (!pkg.isNullOrBlank()) {
                            val set = result.getOrPut(drawable) { LinkedHashSet() }
                            set.add(pkg)
                        }
                    }
                }
                event = parser.nextOrNull()
            }
        } catch (_: Exception) {
            return emptyMap()
        } finally {
            try {
                parser.close()
            } catch (_: Exception) {
            }
        }

        return result.mapValues { (_, set) -> set.toList().sorted() }
    }
}

private fun String.toPackageName(): String? {
    if (isBlank()) return null

    // Typical icon pack format: ComponentInfo{com.foo/.MainActivity}
    // If braces are empty (ComponentInfo{}), there is no package.
    val insideBraces = substringAfter('{', this).substringBefore('}', this).trim()
    if (insideBraces.isBlank()) return null

    val pkg = insideBraces.substringBefore('/').substringBefore(' ').trim()
    if (pkg.isBlank()) return null

    // Basic sanity check: must look like a package name (contain at least one dot).
    if (!pkg.contains('.')) return null

    return pkg
}
