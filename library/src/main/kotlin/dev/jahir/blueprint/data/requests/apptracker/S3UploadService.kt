package dev.jahir.blueprint.data.requests.apptracker

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Url

/**
 * Retrofit service interface for uploading files to S3 using signed URLs
 */
interface S3UploadService {
    /**
     * Upload a file to S3 using a pre-signed URL.
     * @param url The signed S3 upload URL (provided dynamically)
     * @param contentType MIME type of the file (e.g., "image/png")
     * @param file The file content as a RequestBody
     * @return Response body from S3
     */
    @PUT
    suspend fun uploadToS3(
        @Url url: String,
        @Header("Content-Type") contentType: String,
        @Body file: RequestBody
    ): ResponseBody
}
