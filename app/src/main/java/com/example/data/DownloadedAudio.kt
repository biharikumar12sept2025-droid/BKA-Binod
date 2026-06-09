package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "downloaded_audios")
data class DownloadedAudio(
    @PrimaryKey val id: String, // YouTube Video ID
    val videoUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val localPath: String,
    val format: String,
    val bitrate: String,
    val size: Long,
    val status: String, // "QUEUED", "DOWNLOADING", "COMPLETED", "FAILED"
    val progress: Int, // 0 - 100
    val speed: String, // e.g. "1.5 MB/s"
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface DownloadedAudioDao {
    @Query("SELECT * FROM downloaded_audios ORDER BY timestamp DESC")
    fun getAllAudios(): Flow<List<DownloadedAudio>>

    @Query("SELECT * FROM downloaded_audios WHERE id = :id LIMIT 1")
    suspend fun getAudioById(id: String): DownloadedAudio?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(audio: DownloadedAudio)

    @Query("DELETE FROM downloaded_audios WHERE id = :id")
    suspend fun deleteAudioById(id: String)

    @Query("UPDATE downloaded_audios SET status = :status, progress = :progress, speed = :speed, size = :size, localPath = :localPath WHERE id = :id")
    suspend fun updateDownloadProgress(id: String, status: String, progress: Int, speed: String, size: Long, localPath: String)
}

@Database(entities = [DownloadedAudio::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedAudioDao(): DownloadedAudioDao
}

class AudioRepository(private val dao: DownloadedAudioDao) {
    val allAudios: Flow<List<DownloadedAudio>> = dao.getAllAudios()

    suspend fun getAudioById(id: String): DownloadedAudio? {
        return dao.getAudioById(id)
    }

    suspend fun insertAudio(audio: DownloadedAudio) {
        dao.insertAudio(audio)
    }

    suspend fun deleteAudio(id: String) {
        dao.deleteAudioById(id)
    }

    suspend fun updateProgress(id: String, status: String, progress: Int, speed: String, size: Long, localPath: String) {
        dao.updateDownloadProgress(id, status, progress, speed, size, localPath)
    }
}
