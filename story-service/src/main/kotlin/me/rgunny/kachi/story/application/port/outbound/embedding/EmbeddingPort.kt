package me.rgunny.kachi.story.application.port.outbound.embedding

import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.EmbeddingText

/**
 * 텍스트를 벡터로 바꾸는 출력 포트.
 */
interface EmbeddingPort {

    val model: EmbeddingModel

    suspend fun embed(texts: List<EmbeddingText>): List<Embedding>
}
