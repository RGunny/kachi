package me.rgunny.kachi.story.fake

import kotlin.math.sqrt
import kotlin.random.Random
import me.rgunny.kachi.story.application.port.outbound.embedding.EmbeddingPort
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.domain.EmbeddingText

/**
 * 텍스트 해시로 결정적 단위 벡터를 만드는 임베딩 포트.
 *
 * 같은 텍스트는 같은 벡터이고 다른 텍스트는 거의 직교한다.
 * [fixed]에 둔 텍스트는 그 벡터를 돌려준다.
 */
class FakeEmbeddingPort(
    override val model: EmbeddingModel = EmbeddingModel.BGE_M3
) : EmbeddingPort {

    val fixed: MutableMap<EmbeddingText, Embedding> = mutableMapOf()
    val embeddedTexts: MutableList<EmbeddingText> = mutableListOf()
    var failure: Throwable? = null

    override suspend fun embed(texts: List<EmbeddingText>): List<Embedding> {
        embeddedTexts += texts
        failure?.let { throw it }

        return texts.map { text -> fixed[text] ?: deterministic(text) }
    }

    private fun deterministic(text: EmbeddingText): Embedding {
        val random = Random(text.value.hashCode())
        val values = FloatArray(model.dimension) { random.nextFloat() * 2 - 1 }
        val norm = sqrt(values.sumOf { it.toDouble() * it }).toFloat()

        return Embedding.of(model, FloatArray(values.size) { values[it] / norm })
    }
}
