package me.rgunny.kachi.story.domain

import kotlin.math.sqrt

/**
 * 임베딩 모델이 만든 벡터 하나.
 */
class Embedding private constructor(
    val model: EmbeddingModel,
    private val vector: FloatArray
) {
    val values: FloatArray
        get() = vector.copyOf()

    val dimension: Int
        get() = vector.size

    companion object {

        fun of(model: EmbeddingModel, values: FloatArray): Embedding {
            require(values.size == model.dimension) {
                "임베딩 차원이 모델과 다릅니다: model=${model.code}, expected=${model.dimension}, actual=${values.size}"
            }
            require(values.all { it.isFinite() }) { "임베딩 값은 유한해야 합니다" }

            return Embedding(model, values.copyOf())
        }
    }

    /**
     * 코사인 유사도.
     *
     * 어느 한쪽이 영벡터면 0이고, 부동소수 오차로 범위를 벗어난 값은 -1과 1로 자른다.
     */
    fun cosine(other: Embedding): Double {
        requireSameModel(other)

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vector.indices) {
            val a = vector[i].toDouble()
            val b = other.vector[i].toDouble()
            dot += a * b
            normA += a * a
            normB += b * b
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0
        }

        return (dot / (sqrt(normA) * sqrt(normB))).coerceIn(-1.0, 1.0)
    }

    /**
     * 가중 평균. 이 벡터가 [weight]개, [other]가 [otherWeight]개의 평균일 때 둘을 합친 평균이다.
     */
    fun meanWith(other: Embedding, weight: Int, otherWeight: Int): Embedding {
        requireSameModel(other)
        require(weight > 0 && otherWeight > 0) { "평균의 가중치는 양수여야 합니다: $weight, $otherWeight" }

        val total = (weight + otherWeight).toDouble()
        val merged = FloatArray(vector.size) { i ->
            ((vector[i].toDouble() * weight + other.vector[i].toDouble() * otherWeight) / total).toFloat()
        }

        return Embedding(model, merged)
    }

    private fun requireSameModel(other: Embedding) {
        require(model == other.model) { "다른 모델의 임베딩입니다: ${model.code}, ${other.model.code}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Embedding) return false

        return model == other.model && vector.contentEquals(other.vector)
    }

    override fun hashCode(): Int = 31 * model.hashCode() + vector.contentHashCode()

    override fun toString(): String = "Embedding(model=${model.code}, dimension=$dimension)"
}
