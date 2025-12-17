package dev.jahir.blueprint.extensions

import android.content.Context
import android.os.Build
import dev.jahir.blueprint.R
import dev.jahir.blueprint.data.models.Icon
import dev.jahir.frames.extensions.resources.getAttributeValue
import dev.jahir.frames.extensions.resources.nextOrNull
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

internal fun Context.getIconDisplayName(icon: Icon): String {
    val fallback = icon.name
    val drawableName = try {
        resources.getResourceEntryName(icon.resId)
    } catch (_: Exception) {
        null
    }

    if (drawableName.isNullOrBlank()) return fallback

    IconAppLabelMapping.getInstalledAppLabel(this, drawableName)?.let { return it }

    return IconNameMapping.get(this)[drawableName].orEmpty().ifBlank { fallback }
}

private object IconNameMapping {

    private val lock = Any()

    @Volatile
    private var cachedPackageName: String? = null

    @Volatile
    private var cachedLocaleTag: String? = null

    @Volatile
    private var cachedMap: Map<String, String>? = null

    fun get(context: Context): Map<String, String> {
        val pkg = context.packageName
        val localeTag = context.getPrimaryLocaleTag()
        val current = cachedMap
        if (current != null && cachedPackageName == pkg && cachedLocaleTag == localeTag) return current

        synchronized(lock) {
            val second = cachedMap
            if (second != null && cachedPackageName == pkg && cachedLocaleTag == localeTag) return second

            val loaded = load(context)
            cachedPackageName = pkg
            cachedLocaleTag = localeTag
            cachedMap = loaded
            return loaded
        }
    }

    private fun load(context: Context): Map<String, String> {
        val xmlId = run {
            val res = context.resources
            val name = "appname"
            val type = "xml"

            val appId = try {
                res.getIdentifier(name, type, context.packageName)
            } catch (_: Exception) {
                0
            }
            if (appId != 0) return@run appId

            val libPkg = try {
                dev.jahir.blueprint.R::class.java.`package`?.name
            } catch (_: Exception) {
                null
            }

            try {
                res.getIdentifier(name, type, libPkg)
            } catch (_: Exception) {
                0
            }
        }
        if (xmlId == 0) return emptyMap()

        val result = HashMap<String, String>()
        val parser = try {
            context.resources.getXml(xmlId)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        try {
            var event: Int? = parser.eventType
            while (event != null && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val key = parser.getAttributeValue("drawable")
                        ?: parser.getAttributeValue("icon")
                        ?: parser.getAttributeValue("file")
                        ?: parser.getAttributeValue("key")

                    val value = parser.getAttributeValue("cn")
                        ?: parser.getAttributeValue("zh")
                        ?: parser.getAttributeValue("label")
                        ?: parser.getAttributeValue("title")
                        ?: parser.getAttributeValue("name")

                    if (!key.isNullOrBlank() && !value.isNullOrBlank()) {
                        result[key.trim()] = value.trim()
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

        return result
    }
}

private fun Context.getPrimaryLocaleTag(): String {
    return try {
        val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale ?: Locale.getDefault()
        }
        locale.toLanguageTag()
    } catch (_: Exception) {
        Locale.getDefault().toLanguageTag()
    }
}

private object IconAppLabelMapping {

    private val lock = Any()

    @Volatile
    private var cachedPackageName: String? = null

    @Volatile
    private var cachedMap: Map<String, List<String>>? = null

    fun getInstalledAppLabel(context: Context, drawableName: String): String? {
        val packages = get(context)[drawableName].orEmpty()
        if (packages.isEmpty()) return null

        val pm = context.packageManager
        for (pkg in packages) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info)?.toString().orEmpty()
                if (label.isNotBlank()) return label
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun get(context: Context): Map<String, List<String>> {
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
        val result = HashMap<String, MutableSet<String>>()

        val parser = try {
            context.resources.getXml(R.xml.appfilter)
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        try {
            var event: Int? = parser.eventType
            while (event != null && event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val drawable = parser.getAttributeValue("drawable")?.trim().orEmpty()
                    val component = parser.getAttributeValue("component")?.trim().orEmpty()
                    val pkg = component.toPackageName()
                    if (drawable.isNotBlank() && !pkg.isNullOrBlank()) {
                        val set = result.getOrPut(drawable) { LinkedHashSet() }
                        set.add(pkg)
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
    val insideBraces = substringAfter('{', this).substringBefore('}', this)
    val candidate = if (insideBraces.isNotBlank()) insideBraces else this
    val pkg = candidate.substringBefore('/').substringBefore(' ')
    return pkg.trim().ifBlank { null }
}
