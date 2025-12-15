package dev.jahir.blueprint.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Response from /app-info/create endpoint
 */
@Parcelize
data class AppInfoDTO(
    val defaultName: String,
    val packageName: String,
    val mainActivity: String,
    val id: String? = null,
    val createdAt: String? = null
) : Parcelable

/**
 * Response from /app-icon/generate-upload-url endpoint
 */
@Parcelize
data class AppIconUploadUrlResponse(
    val uploadURL: String
) : Parcelable
