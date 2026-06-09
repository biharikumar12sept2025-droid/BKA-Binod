package com.example.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

data class VideoMetadata(
    val id: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String
)

object NetworkService {
    private const val TAG = "NetworkService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun extractVideoId(url: String): String? {
        val cleanUrl = url.trim()
        val patterns = arrayOf(
            "(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:[^\\/\\n\\s]+\\/\\S+\\/|(?:v|e(?:mbed)?)\\/|\\S*?[?&]v=)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})",
            "(?:https?:\\/\\/)?(?:www\\.)?youtube\\.com\\/shorts\\/([a-zA-Z0-9_-]{11})"
        )
        for (pattern in patterns) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(cleanUrl)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    suspend fun fetchVideoMetadata(videoUrl: String): VideoMetadata? = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoUrl) ?: return@withContext null
        val normalizedUrl = "https://www.youtube.com/watch?v=$videoId"
        val oEmbedUrl = "https://www.youtube.com/oembed?url=${normalizedUrl}&format=json"

        try {
            val request = Request.Builder()
                .url(oEmbedUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to fetch metadata from oEmbed, status: ${response.code}")
                    // Fallback to basic metadata helper if oEmbed fails
                    return@withContext VideoMetadata(
                        id = videoId,
                        url = normalizedUrl,
                        title = "YouTube Track ($videoId)",
                        author = "YouTube Creator",
                        thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                    )
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val jsonObject = JSONObject(bodyString)
                val title = jsonObject.optString("title", "YouTube Video")
                val author = jsonObject.optString("author_name", "Unknown Channel")
                val thumbnailUrl = jsonObject.optString("thumbnail_url", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")

                return@withContext VideoMetadata(
                    id = videoId,
                    url = normalizedUrl,
                    title = title,
                    author = author,
                    thumbnailUrl = thumbnailUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching YouTube metadata", e)
            // Fallback
            return@withContext VideoMetadata(
                id = videoId,
                url = normalizedUrl,
                title = "YouTube Audio Extractions ($videoId)",
                author = "YouTube Audio",
                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            )
        }
    }

    suspend fun fetchDownloadUrl(
        videoUrl: String,
        format: String,
        bitrate: String,
        serverUrl: String
    ): String = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(videoUrl) ?: throw IllegalArgumentException("Invalid YouTube URL")
        val cleanServerUrl = serverUrl.trim().removeSuffix("/")
        val apiTarget = "$cleanServerUrl/" // Send directly to the base of the cobalt service

        Log.d(TAG, "Triggering cobalt request to: $apiTarget for format: $format, bitrate: $bitrate")

        // Construct request payload
        // Supporting both Cobalt v7/8 (audioOnly=true, audioFormat=format) and v10 (downloadMode="audio", audioFormat=format)
        val jsonPayload = JSONObject().apply {
            put("url", "https://www.youtube.com/watch?v=$videoId")
            put("downloadMode", "audio")
            put("audioFormat", format)
            put("audioBitrate", bitrate) // Cobalt v10 supporting bitrate quality (e.g. 320, 256, 128, 64)
            put("audioOnly", true) // Cobalt v7 fallback
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(apiTarget)
            .post(requestBody)
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: throw IOException("Empty response from Cobalt API")
                Log.d(TAG, "Cobalt response code: ${response.code}, body: $bodyString")

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val json = JSONObject(bodyString)
                        val errObj = json.optJSONObject("error")
                        errObj?.optString("text") ?: json.optString("text", "Server returned HTTP ${response.code}")
                    } catch (e: Exception) {
                        "Server returned HTTP ${response.code}"
                    }
                    throw IOException(errorMsg)
                }

                val resultJson = JSONObject(bodyString)
                val status = resultJson.optString("status")

                when (status) {
                    "success", "redirect", "stream" -> {
                        val url = resultJson.optString("url")
                        if (url.isNotEmpty()) {
                            return@withContext url
                        } else {
                            throw IOException("Response marked as success but URL is missing.")
                        }
                    }
                    "error" -> {
                        val errorDetail = resultJson.optJSONObject("error")
                        val text = errorDetail?.optString("text") ?: resultJson.optString("text", "Unknown Cobalt API error")
                        throw IOException(text)
                    }
                    "picker" -> {
                        // Sometimes picker returns multiple URLs (slideshows or playlist tracks)
                        val pickerArray = resultJson.optJSONArray("picker")
                        if (pickerArray != null && pickerArray.length() > 0) {
                            val firstItem = pickerArray.getJSONObject(0)
                            val url = firstItem.optString("url")
                            if (url.isNotEmpty()) {
                                return@withContext url
                            }
                        }
                        throw IOException("Multiple audio alternatives detected but none can be played.")
                    }
                    else -> {
                        // Fallback: If no status but url exists directly
                        val url = resultJson.optString("url")
                        if (url.isNotEmpty()) {
                            return@withContext url
                        }
                        throw IOException("Unsupported API status: $status")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error matching download link in Cobalt Server", e)
            throw IOException(e.message ?: "Connection to download server failed.")
        }
    }
}
