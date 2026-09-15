package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * judge·classifier 모델의 라벨 표.
 */
data class TeiClassifierType(
    val id2label: Map<String, String> = emptyMap(),
    val label2id: Map<String, Int> = emptyMap()
)
