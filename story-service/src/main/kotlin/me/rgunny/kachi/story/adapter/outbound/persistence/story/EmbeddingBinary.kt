package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.nio.ByteBuffer
import java.nio.ByteOrder
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.EmbeddingModel

/**
 * 임베딩 벡터와 float32 little-endian 바이트열 사이의 변환기.
 */
object EmbeddingBinary {

    private const val BYTES_PER_VALUE = Float.SIZE_BYTES

    fun encode(embedding: Embedding): ByteArray {
        val values = embedding.values
        val buffer = ByteBuffer.allocate(values.size * BYTES_PER_VALUE).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buffer.putFloat(it) }

        return buffer.array()
    }

    fun decode(model: EmbeddingModel, bytes: ByteArray): Embedding {
        require(bytes.size == model.dimension * BYTES_PER_VALUE) {
            "임베딩 바이트 길이가 모델과 다릅니다: model=${model.code}, expected=${model.dimension * BYTES_PER_VALUE}, actual=${bytes.size}"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val values = FloatArray(model.dimension) { buffer.getFloat() }

        return Embedding.of(model, values)
    }
}
