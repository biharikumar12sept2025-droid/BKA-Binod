package com.example.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class PlayerState(
    val audioId: String? = null,
    val title: String = "",
    val author: String = "",
    val thumbnailUrl: String = "",
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0
)

class AudioPlayer(private val context: Context) {
    private val TAG = "AudioPlayer"
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun play(audioId: String, title: String, author: String, thumbnailUrl: String, filePath: String) {
        Log.d(TAG, "Attempting to play $title from path: $filePath")
        
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: $filePath")
            _playerState.value = PlayerState(isPlaying = false)
            return
        }

        try {
            stop() // Stop and release existing media player first

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                start()
            }

            _playerState.value = PlayerState(
                audioId = audioId,
                title = title,
                author = author,
                thumbnailUrl = thumbnailUrl,
                isPlaying = true,
                currentPosition = 0,
                duration = mediaPlayer?.duration ?: 0
            )

            startProgressTicker()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting playback", e)
            _playerState.value = PlayerState(isPlaying = false)
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
                stopProgressTicker()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
                startProgressTicker()
            }
        }
    }

    fun togglePlayPause() {
        val currentState = _playerState.value
        if (currentState.audioId == null) return

        if (currentState.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _playerState.value = _playerState.value.copy(currentPosition = positionMs)
        }
    }

    fun seekForward(ms: Int = 10000) {
        mediaPlayer?.let {
            val target = (it.currentPosition + ms).coerceAtMost(it.duration)
            seekTo(target)
        }
    }

    fun seekBackward(ms: Int = 10000) {
        mediaPlayer?.let {
            val target = (it.currentPosition - ms).coerceAtLeast(0)
            seekTo(target)
        }
    }

    fun stop() {
        stopProgressTicker()
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        _playerState.value = PlayerState()
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = coroutineScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    try {
                        if (mp.isPlaying) {
                            val currentPos = mp.currentPosition
                            val duration = mp.duration
                            _playerState.value = _playerState.value.copy(
                                currentPosition = currentPos,
                                duration = duration
                            )
                            
                            // Handle auto-next/completion
                            if (currentPos >= duration - 300 && duration > 0) {
                                pause()
                                seekTo(0)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in progress ticker loop", e)
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stop()
        coroutineScope.cancel()
    }
}
