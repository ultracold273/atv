package com.example.atv.data.epg

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wire DTO for a CTC channel as returned by `frameset_builder.jsp`. Mapped to
 * the domain [com.example.atv.domain.model.Channel] by
 * [com.example.atv.domain.usecase.ImportCtcChannelsUseCase].
 */
data class CtcChannelEntry(
    val channelId: String,
    val channelName: String,
    val userChannelId: String,
    val channelUrl: String,
    val displayNumber: Int,
)

/**
 * Fetches the operator channel list. Two HTTP calls:
 *   1. POST `frameset_builder.jsp` — returns HTML with embedded `jsSetConfig('Channel', '...')`
 *      blocks that parse to channel records.
 *   2. GET `get_channel_info_mapping.jsp` — returns JSON with `channelMixnoMapping`
 *      that maps display numbers ("001") to UserChannelID values.
 *
 * Ports `IPTVClient.fetch_channels` + `fetch_channel_mapping` from
 * `~/Documents/itv-reverse/iptv_client.py` lines 377-402.
 */
@Singleton
class CtcChannelFetcher @Inject constructor(
    private val http: OkHttpClient,
) {
    suspend fun fetch(session: LoginResult.Success): Result<List<CtcChannelEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawChannels = fetchFrameset(session)
                val mapping = runCatching { fetchMapping(session) }.getOrDefault(emptyMap())
                val byUserCh = mapping.entries.associate { (display, userCh) ->
                    userCh to display.toIntOrNull()
                }
                rawChannels.mapIndexed { idx, raw ->
                    val displayNumber = byUserCh[raw.userChannelId] ?: (idx + 1)
                    raw.copy(displayNumber = displayNumber)
                }
            }.onFailure { Timber.d(it, "CTC fetch channels failed") }
        }

    private fun fetchFrameset(session: LoginResult.Success): List<CtcChannelEntry> {
        val url = "${session.epgLbBase}frameset_builder.jsp".toHttpUrl()
        val form = FormBody.Builder()
            .add("BUILD_ACTION", "FRAMESET_BUILDER")
            .add("MAIN_WIN_SRC", "/iptvepg/frame310/first_channel_play.jsp?tempno=777")
            .add("NEED_UPDATE_STB", "1")
            .build()
        val req = Request.Builder()
            .url(url)
            .post(form)
            .header("Cookie", "JSESSIONID=${session.jsessionId}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("frameset_builder HTTP ${resp.code}")
            return CtcResponseParsers.parseChannels(resp.body?.string().orEmpty())
        }
    }

    private fun fetchMapping(session: LoginResult.Success): Map<String, String> {
        // mapping endpoint lives at iptvepg/frame224/... not under function/.
        val root = session.epgLbBase.removeSuffix("function/")
        val url = "${root}frame224/datas/get_channel_info_mapping.jsp".toHttpUrl()
        val req = Request.Builder()
            .url(url)
            .header("Cookie", "JSESSIONID=${session.jsessionId}")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyMap()
            return CtcResponseParsers.parseMixnoMapping(resp.body?.string().orEmpty())
        }
    }
}
