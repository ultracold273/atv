package com.example.atv.testing

import com.example.atv.domain.model.Channel

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
    )
}
