package me.rgunny.kachi.story.adapter.outbound.tei

import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiInfoResponse
/**
 * 추론 서버 하나를 부르는 클라이언트.
 *
 * 임베딩 서버는 [embed]만, 판정기 서버는 [rerank]만 받는다.
 */
interface TeiClient {

    /** 입력 순서대로 벡터를 돌려준다. */
    suspend fun embed(texts: List<String>): List<FloatArray>

    /** [texts] 순서대로 0~1 점수를 돌려준다. */
    suspend fun rerank(query: String, texts: List<String>): List<Double>

    suspend fun info(): TeiInfoResponse
}
