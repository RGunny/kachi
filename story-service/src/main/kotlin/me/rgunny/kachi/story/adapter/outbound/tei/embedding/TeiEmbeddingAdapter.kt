package me.rgunny.kachi.story.adapter.outbound.tei.embedding

import me.rgunny.kachi.story.adapter.outbound.tei.TeiClient
import me.rgunny.kachi.story.application.port.outbound.embedding.EmbeddingPort
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.EmbeddingText

/**
 * 임베딩 서버로 [EmbeddingPort]를 구현하는 adapter.
 *
 * 입력을 [batchSize]씩 끊어 순서대로 부른다.
 */
class TeiEmbeddingAdapter(
    private val client: TeiClient,
    override val model: EmbeddingModel,
    private val batchSize: Int
) : EmbeddingPort {

    init {
        require(batchSize >= 1) { "임베딩 batch 크기는 1 이상이어야 합니다: $batchSize" }
    }

    override suspend fun embed(texts: List<EmbeddingText>): List<Embedding> {
        if (texts.isEmpty()) {
            return emptyList()
        }

        return texts
            .chunked(batchSize)
            .flatMap { batch -> client.embed(batch.map { it.value }) }
            .map { values -> Embedding.of(model, values) }
    }
}
