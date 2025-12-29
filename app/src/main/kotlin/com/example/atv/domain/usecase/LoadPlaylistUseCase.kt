package com.example.atv.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Use case for loading a playlist from a file URI.
 */
class LoadPlaylistUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parseM3U8UseCase: ParseM3U8UseCase,
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository
) {
    
    /**
     * Load a playlist from a file URI.
     * 
     * @param uri The content URI of the M3U8 file
     * @return Result containing the list of channels or an error
     */
    suspend operator fun invoke(uri: Uri): Result<List<Channel>> {
        return try {
            Timber.d("Loading playlist from: $uri")
            
            // Read file content
            val content = readFileContent(uri)
            if (content.isBlank()) {
                return Result.failure(Exception("File is empty"))
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
}
