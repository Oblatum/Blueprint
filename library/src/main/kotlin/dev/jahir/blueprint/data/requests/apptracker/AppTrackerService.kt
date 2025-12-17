/*
 * Copyright (c) 2025 Ding2FTW
 * This file is part of Blueprint and is licensed under CC-BY-SA 4.0.
 * See LICENSE.md for details.
 */
package dev.jahir.blueprint.data.requests.apptracker

import dev.jahir.blueprint.data.models.AppIconUploadUrlResponse
import dev.jahir.blueprint.data.models.AppInfoDTO
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service interface for AppTracker API
 */
interface AppTrackerService {
    /**
     * Create or update app information with localized names.
     * @param authorization Bearer token in format "Bearer {access_key}"
     * @param apps List of app info requests
     * @return List of created/updated app info DTOs
     */
    @Headers("Accept: application/json")
    @POST("app-info/create")
    suspend fun createAppInfo(
        @Header("Authorization") authorization: String,
        @Body apps: List<AppInfoCreateRequest>
    ): List<AppInfoDTO>

    /**
     * Generate a signed S3 upload URL for an app icon.
     * @param authorization Bearer token in format "Bearer {access_key}"
     * @param packageName Android package name
     * @return Response containing the signed S3 upload URL
     */
    @Headers("Accept: application/json")
    @GET("app-icon/generate-upload-url")
    suspend fun generateUploadUrl(
        @Header("Authorization") authorization: String,
        @Query("packageName") packageName: String
    ): AppIconUploadUrlResponse
}
