package me.rgunny.kachi.collector.adapter.outbound.client.google

data class GoogleRssItem(
    val title: String,
    val link: String,
    val pubDate: String?,
    val sourceName: String?
)
