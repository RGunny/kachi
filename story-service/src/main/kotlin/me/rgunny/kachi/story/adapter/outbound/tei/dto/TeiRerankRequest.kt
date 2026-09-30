package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * `POST /rerank` 요청 본문.
 *
 * `raw_scores: false`면 서버가 라벨 하나에 sigmoid를 걸어 0~1로 준다.
 */
data class TeiRerankRequest(
    val query: String,
    val texts: List<String>,
    val raw_scores: Boolean = false,
    val truncate: Boolean = true
)
