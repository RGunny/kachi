package me.rgunny.kachi.collector.adapter.`in`.web

data class CollectNewsRequest(
    val keywords: List<String> = emptyList(),
    val sources: Set<String> = emptySet()
)
