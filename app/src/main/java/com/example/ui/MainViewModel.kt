package com.example.ui

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AudioRepository
import com.example.data.DownloadedAudio
import com.example.network.NetworkService
import com.example.network.VideoMetadata
import com.example.player.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

data class SearchState(
    val urlInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val activeMetadata: VideoMetadata? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"

    // Database Initialization
    private val database: AppDatabase by lazy {
        Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "downloaded_audios_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    private val repository: AudioRepository by lazy {
        AudioRepository(database.downloadedAudioDao())
    }

    // Services
    val audioPlayer = AudioPlayer(application)

    // UI Configuration States
    val selectedFormat = MutableStateFlow("mp3")
    val selectedBitrate = MutableStateFlow("320")
    val selectedServer = MutableStateFlow("https://api.cobalt.tools")

    // Search and Link Parse States
    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    // Download List observed directly from DB
    val downloadHistory: StateFlow<List<DownloadedAudio>> = repository.allAudios
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Player State observed directly from Player
    val playerState = audioPlayer.playerState

    // Available Cobalt Server list
    val serverList = listOf(
        "https://api.cobalt.tools" to "Cobalt Official (Default)",
        "https://cobalt.api.rybnd.com" to "Ryband Mirror (Premium)",
        "https://cobalt.percept0r.tech" to "Perceptor Host (High Speed)",
        "https://co.wuk.sh" to "Wuksh Alternative"
    )

    // Format list
    val formatList = listOf("mp3", "m4a", "wav", "opus")

    // Bitrate quality list
    val bitrateList = listOf(
        "320" to "320 kbps (Extreme Fidelity)",
        "256" to "256 kbps (High Quality)",
        "128" to "128 kbps (Standard Stream)",
        "64" to "64 kbps (Data Saver)"
    )

    fun onUrlChange(newUrl: String) {
        _searchState.value = _searchState.value.copy(
            urlInput = newUrl,
            error = null
        )
    }

    fun parseUrl() {
        val url = _searchState.value.urlInput.trim()
        if (url.isEmpty()) {
            _searchState.value = _searchState.value.copy(error = "Please enter a YouTube link")
            return
        }

        val videoId = NetworkService.extractVideoId(url)
        if (videoId == null) {
            _searchState.value = _searchState.value.copy(error = "Invalid YouTube URL format")
            return
        }

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true, error = null, activeMetadata = null)
            val meta = NetworkService.fetchVideoMetadata(url)
            if (meta != null) {
                _searchState.value = _searchState.value.copy(
                    isLoading = false,
                    activeMetadata = meta
                )
            } else {
                _searchState.value = _searchState.value.copy(
                    isLoading = false,
                    error = "Failed to parse video. Please verify the URL or try another."
                )
            }
        }
    }

    fun clearSearch() {
        _searchState.value = SearchState()
    }

    fun startAudioDownload(meta: VideoMetadata) {
        val format = selectedFormat.value
        val bitrate = selectedBitrate.value
        val server = selectedServer.value

        viewModelScope.launch {
            // First Insert basic placeholder entity to Database so user sees "Queued" immediately!
            val placeholder = DownloadedAudio(
                id = meta.id,
                videoUrl = meta.url,
                title = meta.title,
                author = meta.author,
                thumbnailUrl = meta.thumbnailUrl,
                localPath = "",
                format = format,
                bitrate = bitrate,
                size = 0L,
                status = "QUEUED",
                progress = 0,
                speed = "Connecting..."
            )
            repository.insertAudio(placeholder)

            // Clear active parsed metadata display to let them queue another if they wish!
            clearSearch()

            // Perform actual background extraction and downloading
            downloadTaskFlow(meta.id, meta.url, format, bitrate, server)
        }
    }

    private fun downloadTaskFlow(
        videoId: String,
        videoUrl: String,
        format: String,
        bitrate: String,
        serverUrl: String
    ) {
        viewModelScope.launch {
            repository.updateProgress(videoId, "DOWNLOADING", 0, "Initializing Server...", 0L, "")

            try {
                // 1. Fetch direct streaming URL from Cobalt
                val directDownloadUrl = NetworkService.fetchDownloadUrl(videoUrl, format, bitrate, serverUrl)
                Log.d(TAG, "Completed extraction from Cobalt! Stream URL: $directDownloadUrl")

                // 2. Prepare file destination path
                val appMusicDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    ?: getApplication<Application>().filesDir
                
                // Sanitize file name
                val rawTitle = downloadHistory.value.find { it.id == videoId }?.title ?: "Track_$videoId"
                val cleanFileName = sanitizeFileName("${rawTitle}_$bitrate.$format")
                val destinationFile = File(appMusicDir, cleanFileName)

                // 3. Download the streaming file natively with okhttp to support progress indicators!
                performDownload(videoId, directDownloadUrl, destinationFile)

            } catch (e: Exception) {
                Log.e(TAG, "Download failed for $videoId", e)
                repository.updateProgress(
                    id = videoId,
                    status = "FAILED",
                    progress = 0,
                    speed = e.localizedMessage ?: "Network failed",
                    size = 0L,
                    localPath = ""
                )
            }
        }
    }

    private suspend fun performDownload(videoId: String, url: String, destinationFile: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val okHttpClient = OkHttpClient.Builder().build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty stream body")
                val contentLength = body.contentLength()
                val bufferedSource = body.source()

                destinationFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    val startTime = System.currentTimeMillis()
                    var lastUpdateTime = System.currentTimeMillis()

                    while (bufferedSource.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 400 || totalRead == contentLength) {
                            lastUpdateTime = now
                            val durationDecimal = (now - startTime) / 1000.0
                            val speedStr = if (durationDecimal > 0) {
                                val bytesPerSec = totalRead / durationDecimal
                                if (bytesPerSec > 1024 * 1024) {
                                    String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
                                } else {
                                    String.format("%.0f KB/s", bytesPerSec / 1024.0)
                                }
                            } else {
                                "Starting..."
                            }

                            val percent = if (contentLength > 0) {
                                ((totalRead * 100) / contentLength).toInt()
                            } else {
                                -1
                            }

                            repository.updateProgress(
                                videoId,
                                "DOWNLOADING",
                                percent,
                                speedStr,
                                contentLength,
                                destinationFile.absolutePath
                            )
                        }
                    }
                    output.flush()
                }

                // Download Completed Successfully! Update final status in DB!
                repository.updateProgress(
                    videoId,
                    "COMPLETED",
                    100,
                    "Success",
                    destinationFile.length(),
                    destinationFile.absolutePath
                )
            }
        } catch (e: Exception) {
            // Delete incomplete partial file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            throw e
        }
    }

    fun playAudio(audio: DownloadedAudio) {
        audioPlayer.play(
            audioId = audio.id,
            title = audio.title,
            author = audio.author,
            thumbnailUrl = audio.thumbnailUrl,
            filePath = audio.localPath
        )
    }

    fun deleteAudio(audio: DownloadedAudio) {
        viewModelScope.launch {
            // Stop playing if this audio was active
            if (playerState.value.audioId == audio.id) {
                audioPlayer.stop()
            }

            // Remove file
            if (audio.localPath.isNotEmpty()) {
                val file = File(audio.localPath)
                if (file.exists()) {
                    file.delete()
                }
            }

            // Remove from repository DB
            repository.deleteAudio(audio.id)
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
