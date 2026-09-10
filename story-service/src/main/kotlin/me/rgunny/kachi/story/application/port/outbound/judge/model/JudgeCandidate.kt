package me.rgunny.kachi.story.application.port.outbound.judge.model

import me.rgunny.kachi.story.domain.EmbeddingText

/** 판정기에 넣는 후보 하나. */
data class JudgeCandidate(
    val text: EmbeddingText,
    val similarity: Double
) {
    init {
        require(similarity in -1.0..1.0) { "코사인 유사도는 -1과 1 사이여야 합니다: $similarity" }
    }
}
