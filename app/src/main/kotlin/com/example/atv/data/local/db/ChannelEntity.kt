package com.example.atv.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.atv.domain.model.Channel

/**
 * Room entity representing a channel stored in the database.
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey
    val number: Int,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "stream_url")
    val streamUrl: String,
    
    @ColumnInfo(name = "group_title")
    val groupTitle: String?,
    
    @ColumnInfo(name = "logo_url")
    val logoUrl: String?,
    
    @ColumnInfo(name = "is_manually_added")
    val isManuallyAdded: Boolean = false
)

/**
 * Convert entity to domain model.
 */
fun ChannelEntity.toDomain(): Channel = Channel(
    number = number,
    name = name,
    streamUrl = streamUrl,
    groupTitle = groupTitle,
    logoUrl = logoUrl
)

/**
 * Convert domain model to entity.
 */
fun Channel.toEntity(isManuallyAdded: Boolean = false): ChannelEntity = ChannelEntity(
    number = number,
    name = name,
    streamUrl = streamUrl,
    groupTitle = groupTitle,
    logoUrl = logoUrl,
    isManuallyAdded = isManuallyAdded
)
