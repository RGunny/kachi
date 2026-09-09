package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import me.rgunny.kachi.story.fixture.StoryTestFixture.embedding
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Embedding")
class EmbeddingTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("모델 차원과 다른 배열은 거부한다")
        fun rejectWrongDimension() {
            assertFailsWith<IllegalArgumentException> { Embedding.of(EmbeddingModel.BGE_M3, FloatArray(3)) }
        }

        @Test
        @DisplayName("NaN·무한 값은 거부한다")
        fun rejectNonFinite() {
            val values = FloatArray(EmbeddingModel.BGE_M3.dimension).also { it[0] = Float.NaN }

            assertFailsWith<IllegalArgumentException> { Embedding.of(EmbeddingModel.BGE_M3, values) }
        }

        @Test
        @DisplayName("배열은 복사해 두므로 밖에서 바꿔도 값이 변하지 않는다")
        fun copyValues() {
            val values = FloatArray(EmbeddingModel.BGE_M3.dimension).also { it[0] = 1f }
            val embedding = Embedding.of(EmbeddingModel.BGE_M3, values)

            values[0] = 5f
            embedding.values[0] = 7f

            assertEquals(1f, embedding.values[0])
        }
    }

    @Nested
    @DisplayName("cosine()")
    inner class Cosine {

        @Test
        @DisplayName("같은 방향은 1, 직교는 0, 반대는 -1이다")
        fun computeCosine() {
            assertEquals(1.0, embedding(1f, 0f).cosine(embedding(2f, 0f)), 1e-6)
            assertEquals(0.0, embedding(1f, 0f).cosine(embedding(0f, 1f)), 1e-6)
            assertEquals(-1.0, embedding(1f, 0f).cosine(embedding(-1f, 0f)), 1e-6)
        }

        @Test
        @DisplayName("영벡터와의 유사도는 0이다")
        fun zeroVector() {
            assertEquals(0.0, embedding(1f, 0f).cosine(embedding()), 1e-6)
        }
    }

    @Nested
    @DisplayName("meanWith()")
    inner class MeanWith {

        @Test
        @DisplayName("기사 수로 가중한 평균이다")
        fun weightedMean() {
            val mean = embedding(1f, 0f).meanWith(embedding(0f, 1f), 3, 1)

            assertEquals(0.75f, mean.values[0])
            assertEquals(0.25f, mean.values[1])
        }

        @Test
        @DisplayName("가중치가 0 이하이면 거부한다")
        fun rejectNonPositiveWeight() {
            assertFailsWith<IllegalArgumentException> { embedding(1f).meanWith(embedding(1f), 0, 1) }
        }
    }

    @Test
    @DisplayName("같은 모델·같은 값이면 같다")
    fun equality() {
        assertEquals(embedding(1f, 2f), embedding(1f, 2f))
        assertEquals(embedding(1f, 2f).hashCode(), embedding(1f, 2f).hashCode())
        assertNotEquals(embedding(1f, 2f), embedding(2f, 1f))
    }

    @Test
    @DisplayName("문자열 표현에 벡터 값을 쏟지 않는다")
    fun compactToString() {
        assertEquals("Embedding(model=bge-m3, dimension=1024)", embedding(1f).toString())
    }
}
