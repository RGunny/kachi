package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * `POST /rerank` 응답의 한 행.
 *
 * `index`는 입력 순서다.
 */
data class TeiRerankScore(
    val index: Int?,
    val score: Double?
)
