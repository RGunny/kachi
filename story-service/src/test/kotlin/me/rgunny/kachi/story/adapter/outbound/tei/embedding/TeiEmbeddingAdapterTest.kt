package me.rgunny.kachi.story.adapter.outbound.tei.embedding

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.fake.FakeTeiClient
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TeiEmbeddingAdapter")
class TeiEmbeddingAdapterTest {
    private val client = FakeTeiClient(dimension = EmbeddingModel.BGE_M3.dimension)

    @Test
    @DisplayName("batch 크기를 넘는 입력은 나눠 부르고 순서를 지킨다")
    fun splitIntoBatches() = runBlocking {
        val adapter = TeiEmbeddingAdapter(client = client, model = EmbeddingModel.BGE_M3, batchSize = 32)
        val texts = (1..40).map { EmbeddingText.of("제목 $it", "발췌문 $it") }

        val embeddings = adapter.embed(texts)

        assertEquals(2, client.embedCallCount)
        assertEquals(listOf(32, 8), client.embeddedBatches.map { it.size })
        assertEquals(texts.map { it.value }, client.embeddedBatches.flatten())
        assertEquals(40, embeddings.size)
        assertEquals(EmbeddingModel.BGE_M3, embeddings.first().model)
        assertEquals(client.embed(listOf(texts[5].value)).single().toList(), embeddings[5].values.toList())
    }

    @Test
    @DisplayName("빈 입력은 호출 없이 빈 목록이다")
    fun emptyInput() = runBlocking {
        val adapter = TeiEmbeddingAdapter(client = client, model = EmbeddingModel.BGE_M3, batchSize = 32)

        assertEquals(emptyList(), adapter.embed(emptyList()))
        assertEquals(0, client.embedCallCount)
    }

    @Test
    @DisplayName("batch 크기는 1 이상이어야 한다")
    fun rejectNonPositiveBatch() {
        assertFailsWith<IllegalArgumentException> { TeiEmbeddingAdapter(client, EmbeddingModel.BGE_M3, batchSize = 0) }
    }
}
