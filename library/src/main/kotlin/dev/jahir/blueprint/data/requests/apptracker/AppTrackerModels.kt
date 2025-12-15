package dev.jahir.blueprint.data.requests.apptracker

import com.google.gson.annotations.SerializedName

/**
 * Request body for /app-info/create endpoint
 */
data class AppInfoCreateRequest(
    @SerializedName("languageCode")
    val languageCode: String,

    @SerializedName("localizedName")
    val localizedName: String,

    @SerializedName("packageName")
    val packageName: String,

    @SerializedName("mainActivity")
    val mainActivity: String,

    @SerializedName("defaultName")
    val defaultName: String
)

/**
 * Extracts the main activity from a Blueprint component string.
 * Component format: "com.example.app/com.example.app.MainActivity"
 * Returns: "com.example.app.MainActivity"
 */
internal fun String.extractMainActivity(): String {
    return this.split("/").lastOrNull() ?: this
}

/**
 * Gets the device language code in IETF BCP 47 format.
 * Examples: "en", "en-US", "zh-CN", "ja"
 */
internal fun getDeviceLanguageCode(): String {
    return java.util.Locale.getDefault().toLanguageTag()
}
