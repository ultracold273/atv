package com.example.atv.domain.usecase

import com.example.atv.data.parser.M3U8Parser
import com.example.atv.data.parser.ParseResult
import com.example.atv.domain.model.Channel
import javax.inject.Inject

/**
 * Use case for parsing M3U8 playlist content.
 */
class ParseM3U8UseCase @Inject constructor(
    private val parser: M3U8Parser
) {
    
    /**
     * Parse M3U8 content string into channels.
     */
    operator fun invoke(content: String): Result<List<Channel>> {
        return when (val result = parser.parse(content)) {
            is ParseResult.Success -> Result.success(result.channels)
            is ParseResult.Error -> Result.failure(
                PlaylistParseException(result.message, result.lineNumber)
            )
        }
    }
}

/**
 * Exception thrown when playlist parsing fails.
 */
class PlaylistParseException(
    override val message: String,
    val lineNumber: Int? = null
) : Exception(message)
