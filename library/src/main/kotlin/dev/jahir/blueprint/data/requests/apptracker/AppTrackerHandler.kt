package dev.jahir.blueprint.data.requests.apptracker

import android.content.Context
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.GsonBuilder
import dev.jahir.blueprint.data.models.RequestApp
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.io.ByteArrayOutputStream

/**
 * Handler for uploading icon requests to AppTracker backend.
 * Manages both app metadata uploads and icon uploads to S3.
 */
object AppTrackerHandler {
    private var appTrackerService: AppTrackerService? = null
    private var s3UploadService: S3UploadService? = null

    /**
     * Gets or creates the AppTracker Retrofit service instance
     */
    private fun getAppTrackerService(baseUrl: String): AppTrackerService {
        return appTrackerService ?: Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().create()))
            .build()
            .create(AppTrackerService::class.java)
            .also { appTrackerService = it }
    }

    /**
     * Gets or creates the S3 upload Retrofit service instance
     */
    private fun getS3UploadService(): S3UploadService {
        return s3UploadService ?: Retrofit.Builder()
            .baseUrl("https://placeholder.com/") // Not used, URL provided in @Url parameter
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(S3UploadService::class.java)
            .also { s3UploadService = it }
    }

    /**
     * Upload icon requests to AppTracker backend.
     * This performs a two-step process:
     * 1. Upload app metadata to /app-info/create
     * 2. Upload icons in parallel to S3 using signed URLs
     *
     * @param context Android context (unused but kept for consistency)
     * @param selectedApps List of apps to upload
     * @param accessKey AppTracker API access key
     * @param baseUrl AppTracker API base URL
     * @return Pair<Success: Boolean, ErrorMessage: String?>
     */
    suspend fun uploadToAppTracker(
        context: Context?,
        selectedApps: ArrayList<RequestApp>,
        accessKey: String,
        baseUrl: String
    ): Pair<Boolean, String?> {
        context ?: return false to "Context is null"

        Log.d("Blueprint-AppTracker", "Starting upload for ${selectedApps.size} apps to $baseUrl")

        return withContext(IO.limitedParallelism(5)) {
            try {
                val languageCode = getDeviceLanguageCode()
                val authHeader = "Bearer $accessKey"

                Log.d("Blueprint-AppTracker", "Device language: $languageCode")

                // Step 1: Create app info metadata in batches (max 25 per batch to avoid body size limits)
                val appInfoRequests = selectedApps.map { app ->
                    AppInfoCreateRequest(
                        languageCode = languageCode,
                        localizedName = app.name,
                        packageName = app.packageName,
                        mainActivity = app.component.extractMainActivity(),
                        defaultName = app.name
                    )
                }

                val service = getAppTrackerService(baseUrl)
                val batchSize = 25
                val batches = appInfoRequests.chunked(batchSize)
                Log.d("Blueprint-AppTracker", "Uploading metadata in ${batches.size} batch(es)")

                batches.forEachIndexed { index, batch ->
                    Log.d("Blueprint-AppTracker", "Uploading batch ${index + 1}/${batches.size} (${batch.size} apps)")
                    service.createAppInfo(authHeader, batch)
                    Log.d("Blueprint-AppTracker", "Batch ${index + 1} uploaded successfully")
                }

                Log.d("Blueprint-AppTracker", "All metadata uploaded, starting icon uploads")

                // Step 2: Upload icons in parallel (with throttling via limitedParallelism)
                val uploadResults = selectedApps.map { app ->
                    async {
                        uploadSingleIcon(app, authHeader, service)
                    }
                }.awaitAll()

                // Check if any uploads failed
                val failedUploads = uploadResults.filter { !it.first }
                if (failedUploads.isNotEmpty()) {
                    val errorMsg = failedUploads.firstOrNull()?.second
                    Log.e("Blueprint-AppTracker", "${failedUploads.size} icon(s) failed to upload: $errorMsg")
                    return@withContext false to "Some icons failed to upload: $errorMsg"
                }

                Log.d("Blueprint-AppTracker", "All ${selectedApps.size} icons uploaded successfully")
                true to null
            } catch (e: HttpException) {
                val errorMsg = "HTTP Error: ${e.code()} - ${e.message()}"
                Log.e("Blueprint-AppTracker", errorMsg, e)
                false to errorMsg
            } catch (e: Exception) {
                val errorMsg = "Upload failed: ${e.message}"
                Log.e("Blueprint-AppTracker", errorMsg, e)
                false to errorMsg
            }
        }
    }

    /**
     * Upload a single app's icon to S3.
     * Gets a signed URL from AppTracker, then uploads the PNG to S3.
     *
     * @param app The app whose icon to upload
     * @param authHeader Bearer authorization header
     * @param service AppTracker service instance
     * @return Pair<Success: Boolean, ErrorMessage: String?>
     */
    private suspend fun uploadSingleIcon(
        app: RequestApp,
        authHeader: String,
        service: AppTrackerService
    ): Pair<Boolean, String?> {
        return try {
            Log.d("Blueprint-AppTracker", "Getting upload URL for ${app.packageName}")

            // Get signed upload URL from AppTracker
            val urlResponse = service.generateUploadUrl(authHeader, app.packageName)
            Log.d("Blueprint-AppTracker", "Got S3 upload URL for ${app.packageName}")

            // Convert icon drawable to PNG bytes
            val bitmap = app.icon?.toBitmap()
                ?: return (false to "Icon is null for ${app.packageName}").also {
                    Log.w("Blueprint-AppTracker", "Icon is null for ${app.packageName}")
                }
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            val pngBytes = outputStream.toByteArray()
            Log.d("Blueprint-AppTracker", "Converted ${app.packageName} icon to PNG (${pngBytes.size} bytes)")

            // Upload to S3 using signed URL
            val requestBody = pngBytes.toRequestBody("image/png".toMediaTypeOrNull())
            val s3Service = getS3UploadService()
            s3Service.uploadToS3(urlResponse.uploadURL, "image/png", requestBody)

            Log.d("Blueprint-AppTracker", "Successfully uploaded icon for ${app.packageName}")
            true to null
        } catch (e: Exception) {
            val errorMsg = "Failed to upload icon for ${app.packageName}: ${e.message}"
            Log.e("Blueprint-AppTracker", errorMsg, e)
            false to errorMsg
        }
    }
}
