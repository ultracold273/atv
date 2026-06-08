package com.example.atv.data.epg

import com.example.atv.domain.model.Program
import com.example.atv.domain.repository.EpgProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EPG provider for China Telecom (CTC) IPTV.
 *
 * Cache: in-memory bounded LRU keyed by (channelCode, dateOffset). TTL = 60s.
 * Single-flight: concurrent fetches for the same key share one in-flight network call.
 * Retry: one silent retry after 1500ms on IOException or HTTP 5xx; second failure surfaces.
 *
 * `isConfigured` is permanently false in spec 004 — the trigger to flip it true ships in 005.
 */
@Singleton
class CtcEpgProvider @Inject constructor(
    private val authClient: CtcAuthClient,
    private val http: OkHttpClient,
    private val device: DeviceProfile,
    private val clock: Clock,
) : EpgProvider {

    /**
     * Test seam: construct with a custom cache size. Production uses the default.
     * Implemented as a secondary constructor (not a default parameter) because
     * Hilt's annotation processor does not honor Kotlin default values.
     */
    internal constructor(
        authClient: CtcAuthClient,
        http: OkHttpClient,
        device: DeviceProfile,
        clock: Clock,
        maxCacheEntries: Int,
    ) : this(authClient, http, device, clock) {
        this.maxCacheEntriesOverride = maxCacheEntries
    }

    private var maxCacheEntriesOverride: Int? = null

    // TODO(005): set true after a successful login()
    private val _isConfigured = MutableStateFlow(false)
    override val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private data class CacheKey(val channelCode: String, val dateOffset: Int)
    private data class CacheEntry(val programs: List<Program>, val storedAtNanos: Long)

    // Lazy so the secondary-constructor override (set AFTER `this(...)` delegation
    // completes) is observed at first cache access, not at field-init time.
    private val cache: LinkedLruCache<CacheKey, CacheEntry> by lazy {
        LinkedLruCache(maxCacheEntriesOverride ?: DEFAULT_MAX_ENTRIES)
    }
    private val keyMutexes = HashMap<CacheKey, Mutex>()
    private val mutexLock = Any()

    @Volatile
    private var session: LoginResult.Success? = null

    /** Test-only hatch to flip configured state without going through 005's trigger. */
    internal fun testSetConfigured(value: Boolean) {
        _isConfigured.value = value
    }

    override suspend fun fetchPrograms(
        channelCode: String,
        dateOffset: Int,
    ): Result<List<Program>> = withContext(Dispatchers.IO) {
        if (!_isConfigured.value) {
            return@withContext Result.failure(IllegalStateException("Provider not configured"))
        }
        val key = CacheKey(channelCode, dateOffset)

        cacheGetFresh(key)?.let { return@withContext Result.success(it) }

        val mutex = keyMutex(key)
        mutex.withLock {
            cacheGetFresh(key)?.let { return@withLock Result.success(it) }
            try {
                val programs = ensureSession()
                    .let { sess -> fetchWithRetry(sess, channelCode, dateOffset) }
                cachePut(key, programs)
                Result.success(programs)
            } catch (e: Throwable) {
                Timber.d(e, "CTC fetchPrograms failed for %s/%d", channelCode, dateOffset)
                Result.failure(e)
            }
            // NOTE: the keyMutex is intentionally NOT removed from the map after use.
            // Removing it would race with concurrent callers that already obtained the
            // same Mutex reference but haven't entered withLock yet — they'd be left
            // waiting on an orphaned Mutex while a third caller getOrPuts a fresh one.
            // Keeping mutex entries in the map until the provider is GC'd costs ~few
            // bytes per unique (channelCode, dateOffset) pair; bounded by the LRU cache
            // size in practice, which is itself bounded.
        }
    }

    private suspend fun ensureSession(): LoginResult.Success {
        session?.let { return it }
        return relogin()
    }

    private suspend fun relogin(): LoginResult.Success {
        val r = authClient.login()
        if (r is LoginResult.Success) {
            session = r
            return r
        }
        throw IOException("login failed: ${(r as LoginResult.Failure).reason}")
    }

    private suspend fun fetchWithRetry(
        sess: LoginResult.Success,
        channelCode: String,
        dateOffset: Int,
    ): List<Program> {
        return runCatching { fetchOnce(sess, channelCode, dateOffset) }
            .recoverCatching { first ->
                when {
                    first is SessionExpiredException -> {
                        // HTML received instead of JSON — JSESSIONID went stale on the server.
                        // Invalidate the cached session, re-login, retry once. This is the
                        // diagnosis-not-silent-fallback path that replaces the python ref's
                        // _loads_lenient embedded-JSON extraction hack.
                        session = null
                        val freshSession = relogin()
                        fetchOnce(freshSession, channelCode, dateOffset)
                    }
                    first.isRetryable() -> {
                        delay(RETRY_DELAY_MS)
                        fetchOnce(sess, channelCode, dateOffset)
                    }
                    else -> throw first
                }
            }
            .getOrThrow()
    }

    private fun Throwable.isRetryable(): Boolean =
        this is IOException && this !is SessionExpiredException ||
            this is RetryableHttpException

    private fun fetchOnce(
        sess: LoginResult.Success,
        channelCode: String,
        dateOffset: Int,
    ): List<Program> {
        val url = buildPrevueUrl(sess.epgLbBase, channelCode, dateOffset)
        val req = Request.Builder()
            .url(url)
            .header("Cookie", "JSESSIONID=${sess.jsessionId}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (resp.code in 500..599) {
                throw RetryableHttpException(resp.code)
            }
            if (!resp.isSuccessful) {
                throw IOException("prevue_list HTTP ${resp.code}")
            }
            // Detect the "session expired → server returned an HTML login page" case
            // BEFORE handing the body to the strict JSON parser. We sniff the Content-Type
            // header rather than the body to avoid a chicken-and-egg with very small JSON.
            val contentType = resp.header("Content-Type").orEmpty()
            if (contentType.startsWith("text/html", ignoreCase = true)) {
                throw SessionExpiredException("Got Content-Type=$contentType — assuming JSESSIONID expired")
            }
            val body = resp.body?.string().orEmpty()
            return CtcResponseParsers.parsePrograms(body)
        }
    }

    private fun buildPrevueUrl(epgLbBase: String, channelCode: String, dateOffset: Int): okhttp3.HttpUrl {
        // epgLbBase ends with "iptvepg/function/" (matches python `epg_lb_base`). The prevue URL
        // is anchored at "iptvepg/" so we strip the trailing "function/" segment, mirroring
        // python `_epg_root()` (lines 508-511).
        val root = epgLbBase.removeSuffix("function/")
        val full = root + "frame1194/CHANNEL_PLAYER_UTILS/datas/prevue_list.jsp"
        return full.toHttpUrl().newBuilder()
            .addQueryParameter("channelcode", channelCode)
            .addQueryParameter("framecode", "frame1194")
            .addQueryParameter("versiondir", "CHANNEL_PLAYER_UTILS")
            .addQueryParameter("dateindex", dateOffset.toString())
            .addQueryParameter("stbtype", "sdr")
            .addQueryParameter("recommpara", "userId=${device.userId}&channelId=1&num=6")
            .addQueryParameter("ajax", "1")
            .build()
    }

    // --- cache + mutex bookkeeping -----------------------------------------

    @Synchronized
    private fun cacheGetFresh(key: CacheKey): List<Program>? {
        val entry = cache[key] ?: return null
        val ageNanos = clock.millis() * 1_000_000L - entry.storedAtNanos
        if (ageNanos > TTL_NANOS) {
            cache.remove(key)
            return null
        }
        return entry.programs
    }

    @Synchronized
    private fun cachePut(key: CacheKey, programs: List<Program>) {
        cache[key] = CacheEntry(programs, clock.millis() * 1_000_000L)
    }

    private fun keyMutex(key: CacheKey): Mutex {
        synchronized(mutexLock) {
            return keyMutexes.getOrPut(key) { Mutex() }
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100
        const val RETRY_DELAY_MS = 1_500L
        val TTL_NANOS = 60L * 1_000_000_000L
    }

    private class RetryableHttpException(val code: Int) : IOException("HTTP $code")
    private class SessionExpiredException(message: String) : IOException(message)
}

/** Tiny LRU on top of LinkedHashMap (access-order). Synchronized externally by caller. */
internal class LinkedLruCache<K, V>(private val cap: Int) :
    LinkedHashMap<K, V>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > cap
}
