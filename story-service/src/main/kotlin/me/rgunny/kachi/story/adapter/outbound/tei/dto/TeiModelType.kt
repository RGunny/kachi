package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * `/info`의 `model_type`.
 *
 * 임베딩·judge·classifier 중 있는 키 하나가 종류다.
 */
data class TeiModelType(
    val embedding: TeiEmbeddingType? = null,
    val reranker: TeiClassifierType? = null,
    val classifier: TeiClassifierType? = null
)
