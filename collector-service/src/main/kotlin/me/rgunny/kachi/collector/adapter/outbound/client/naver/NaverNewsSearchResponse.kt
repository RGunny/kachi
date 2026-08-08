package me.rgunny.kachi.collector.adapter.outbound.client.naver

data class NaverNewsSearchResponse(
    val total: Int? = null,
    val start: Int? = null,
    val display: Int? = null,
    val items: List<NaverNewsItem>? = null
)
