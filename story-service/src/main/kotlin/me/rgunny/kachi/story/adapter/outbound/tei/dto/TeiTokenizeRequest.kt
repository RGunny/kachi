package me.rgunny.kachi.story.adapter.outbound.tei.dto

/** `POST /tokenize` 요청 본문. */
data class TeiTokenizeRequest(
    val inputs: List<String>,
    val add_special_tokens: Boolean = true
)
