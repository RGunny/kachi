package me.rgunny.kachi.collector.adapter.out.client.google

import java.time.Duration

data class GoogleNewsProperties(
    val baseUrl: String = "https://news.google.com/rss/search",
    val languageCode: String = "ko",
    val countryCode: String = "KR",
    val timeout: Duration = Duration.ofSeconds(5)
)
