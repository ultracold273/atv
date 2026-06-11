package com.example.atv.domain.model

/**
 * Temporary extension property bridging 004 (no channelCode on Channel)
 * and 005 (which will add a real `channelCode: String?` field on the data class).
 *
 * In 004 alone this returns `null` for every channel — every EPG fetch path
 * that consults this property therefore short-circuits to the "EPG not available"
 * empty state.
 *
 * TODO(005): delete this file once `Channel.channelCode` is a real field.
 */
val Channel.channelCode: String?
    get() = null
