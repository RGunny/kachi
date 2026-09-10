package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * `POST /tokenize` 응답의 토큰 하나.
 */
data class TeiToken(
    val id: Long?,
    val text: String?,
    val special: Boolean? = null
)
