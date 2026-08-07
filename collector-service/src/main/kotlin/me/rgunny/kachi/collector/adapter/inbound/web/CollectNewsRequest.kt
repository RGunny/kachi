package me.rgunny.kachi.collector.adapter.inbound.web

data class CollectNewsRequest(
    val keywords: List<String> = emptyList(),
    val sources: Set<String> = emptySet()
)
