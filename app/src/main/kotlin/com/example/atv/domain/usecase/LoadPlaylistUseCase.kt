package com.example.atv.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * Use case for loading a playlist from a file URI or HTTP URL.
 */
class LoadPlaylistUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseM3U8UseCase: ParseM3U8UseCase,
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository
) {
    
    companion object {
        private const val HTTP_TIMEOUT_MS = 30000
    }
    
    /**
     * Load a playlist from a file URI or HTTP URL.
     * 
     * @param uri The content URI or HTTP URL of the M3U8 file
     * @return Result containing the list of channels or an error
     */
    suspend operator fun invoke(uri: Uri): Result<List<Channel>> {
        return try {
            Timber.d("Loading playlist from: $uri")
            
            // Read content based on scheme
            val content = when (uri.scheme) {
                "http", "https" -> readHttpContent(uri.toString())
                "content", "file" -> readFileContent(uri)
                else -> return Result.failure(Exception("Unsupported URI scheme: ${uri.scheme}"))
            }
            
            if (content.isBlank()) {
                return Result.failure(Exception("Playlist is empty"))
            }
            
            // Parse the content
            val parseResult = parseM3U8UseCase(content)
            
            parseResult.onSuccess { channels ->
                Timber.d("Parsed ${channels.size} channels")
                
                // Save channels to database
                channelRepository.savePlaylistChannels(channels)
                
                // Save playlist file path
                preferencesRepository.setPlaylistFilePath(uri.toString())
                
                Timber.d("Playlist loaded and saved successfully")
            }.onFailure { error ->
                Timber.e(error, "Failed to parse playlist")
            }
            
            parseResult
        } catch (e: Exception) {
            Timber.e(e, "Failed to load playlist")
            Result.failure(e)
        }
    }
    
    /**
     * Refresh the playlist from the saved file path.
     */
    suspend fun refresh(): Result<List<Channel>> {
        val savedPath = preferencesRepository.getPlaylistFilePath()
        var filePath: String? = null
        
        // Collect the first value
        savedPath.collect { path ->
            filePath = path
            return@collect
        }
        
        return if (filePath != null) {
            invoke(Uri.parse(filePath))
        } else {
            Result.failure(Exception("No playlist file path saved"))
        }
    }
    
    private fun readFileContent(uri: Uri): String {
        val contentResolver: ContentResolver = context.contentResolver
        
        return contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: throw Exception("Could not open file")
    }
    
    /**
     * Read content from an HTTP/HTTPS URL.
     */
    private suspend fun readHttpContent(urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.connectTimeout = HTTP_TIMEOUT_MS
            connection.readTimeout = HTTP_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ATV-IPTV-Player/1.0")
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode ${connection.responseMessage}")
            }
            
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
