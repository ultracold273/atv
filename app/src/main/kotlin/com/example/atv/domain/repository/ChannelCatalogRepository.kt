package com.example.atv.domain.repository

import com.example.atv.domain.model.ChannelCatalogSnapshot

interface ChannelCatalogRepository {
    /** Holds the catalog mutation guard through validation and the short playback handoff. */
    suspend fun <T> withSnapshot(block: suspend (ChannelCatalogSnapshot) -> T): T
}
