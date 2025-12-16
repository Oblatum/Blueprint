package dev.jahir.blueprint.extensions

import android.content.Context
import android.os.Build
import dev.jahir.blueprint.data.models.Icon
import dev.jahir.frames.extensions.resources.getAttributeValue
import dev.jahir.frames.extensions.resources.nextOrNull
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

internal fun Context.getIconDisplayName(icon: Icon): String {
    val fallback = icon.name
    if (!isChineseLanguage()) return fallback

    val drawableName = try {
        resources.getResourceEntryName(icon.resId)
    } catch (_: Exception) {
        null
    }

    if (drawableName.isNullOrBlank()) return fallback
    return IconNameMapping.get(this)[drawableName].orEmpty().ifBlank { fallback }
}

private fun Context.isChineseLanguage(): Boolean {
    val locale: Locale = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
    } catch (_: Exception) {
        Locale.getDefault()
    }
    return locale.language.equals("zh", ignoreCase = true)
}

private object IconNameMapping {

    private val lock = Any()

    @Volatile
    private var cachedPackageName: String? = null

    @Volatile
    private var cachedMap: Map<String, String>? = null

    fun get(context: Context): Map<String, String> {
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

    private fun load(context: Context): Map<String, String> {
        val xmlId = try {
            context.resources.getIdentifier("appname", "xml", context.packageName)
        } catch (_: Exception) {
            0
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
