package com.example.atv.domain.usecase

import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for switching to the next or previous channel.
 */
class SwitchChannelUseCase @Inject constructor(
    private val channelRepository: ChannelRepository,
    private val preferencesRepository: PreferencesRepository
) {
    
    /**
     * Get the next channel in the list (wraps around).
     */
    suspend fun nextChannel(currentChannel: Channel?): Channel? {
        val channels = channelRepository.getAllChannels().first()
        if (channels.isEmpty()) return null
        
        val currentIndex = channels.indexOfFirst { it.number == currentChannel?.number }
        val nextIndex = if (currentIndex < 0 || currentIndex >= channels.size - 1) {
            0 // Wrap to first
        } else {
            currentIndex + 1
        }
        
        val nextChannel = channels[nextIndex]
        preferencesRepository.setLastChannelNumber(nextChannel.number)
        return nextChannel
    }
    
    /**
     * Get the previous channel in the list (wraps around).
     */
    suspend fun previousChannel(currentChannel: Channel?): Channel? {
        val channels = channelRepository.getAllChannels().first()
        if (channels.isEmpty()) return null
        
        val currentIndex = channels.indexOfFirst { it.number == currentChannel?.number }
        val prevIndex = if (currentIndex <= 0) {
            channels.size - 1 // Wrap to last
        } else {
            currentIndex - 1
        }
        
        val prevChannel = channels[prevIndex]
        preferencesRepository.setLastChannelNumber(prevChannel.number)
        return prevChannel
    }
    
    /**
     * Switch to a specific channel by number.
     */
    suspend fun switchToChannel(channelNumber: Int): Channel? {
        val channel = channelRepository.getChannel(channelNumber)
        if (channel != null) {
            preferencesRepository.setLastChannelNumber(channelNumber)
        }
        return channel
    }
    
    /**
     * Get the last watched channel or the first channel.
     */
    suspend fun getInitialChannel(): Channel? {
        val lastNumber = preferencesRepository.getLastChannelNumber().first()
        val channel = channelRepository.getChannel(lastNumber)
        
        // If last channel doesn't exist, return first channel
        return channel ?: channelRepository.getAllChannels().first().firstOrNull()
    }
}
