/*
 * Copyright (c) 2025 Ding2FTW
 * This file is part of Blueprint and is licensed under CC-BY-SA 4.0.
 * See LICENSE.md for details.
 */
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
