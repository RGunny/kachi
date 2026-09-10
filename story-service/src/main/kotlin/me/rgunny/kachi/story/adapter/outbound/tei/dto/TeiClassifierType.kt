package me.rgunny.kachi.story.adapter.outbound.tei.dto

/**
 * 판정기·분류기 모델의 라벨 표.
 */
data class TeiClassifierType(
    val id2label: Map<String, String> = emptyMap(),
    val label2id: Map<String, Int> = emptyMap()
)
