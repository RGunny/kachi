package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * `POST /embed` 요청 본문.
 *
 * `normalize`는 L2 정규화, `truncate`는 서버 한도를 넘는 입력을 자르라는 지시다.
 */
data class TeiEmbedRequest(
    val inputs: List<String>,
    val normalize: Boolean = true,
    val truncate: Boolean = true
)
