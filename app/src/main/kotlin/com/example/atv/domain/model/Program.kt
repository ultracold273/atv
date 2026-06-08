package com.example.atv.domain.model

import java.time.Instant

data class Program(
    val code: String,
    val name: String,
    val start: Instant,
    val end: Instant,
    val isLive: Boolean,
    val isReplayable: Boolean
) {
    fun airsAt(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)

    fun progress(now: Instant): Float {
        val total = end.toEpochMilli() - start.toEpochMilli()
        if (total <= 0L) return 0f
        val elapsed = now.toEpochMilli() - start.toEpochMilli()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
