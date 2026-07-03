package com.example.atv.testing

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.local.db.toEntity
import com.example.atv.domain.model.Channel

class E2eDatabaseSeeder(
    private val channelDao: ChannelDao,
) {
    suspend fun seedEmpty() {
        channelDao.deleteAll()
    }

    suspend fun seedChannels(channels: List<Channel>) {
        channelDao.deleteAll()
        channelDao.insertAll(channels.map { it.toEntity() })
    }
}
