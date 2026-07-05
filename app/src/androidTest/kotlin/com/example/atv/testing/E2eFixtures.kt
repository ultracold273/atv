package com.example.atv.testing

import com.example.atv.domain.model.Channel
import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.model.ProxySettings

object E2eFixtures {
    val playbackChannels = listOf(
        Channel(
            number = 1,
            name = "News One",
            streamUrl = "http://127.0.0.1/stream/news-one.m3u8",
            groupTitle = "News",
        ),
        Channel(
            number = 2,
            name = "Sports Two",
            streamUrl = "http://127.0.0.1/stream/sports-two.m3u8",
            groupTitle = "Sports",
        ),
        Channel(
            number = 3,
            name = "Movies Three",
            streamUrl = "http://127.0.0.1/stream/movies-three.m3u8",
            groupTitle = "Movies",
        ),
    )

    val mockPlaylistContent = """
        #EXTM3U
        #EXTINF:-1 group-title="News",Mock News
        http://127.0.0.1/stream/mock-news.m3u8
        #EXTINF:-1 group-title="Sports",Mock Sports
        http://127.0.0.1/stream/mock-sports.m3u8
    """.trimIndent()

    val directCtcCredentials = IptvCredentials(
        userId = "10086",
        password = "secret",
        stbId = "12345678901234567890123456789012",
        ip = "192.168.1.50",
        mac = "00:11:22:33:44:55",
        authServerUrl = "http://127.0.0.1/auth",
    )

    val proxySettings = ProxySettings(
        proxyBaseUrl = "http://127.0.0.1:8080",
        accessToken = "test-token",
    )
}
