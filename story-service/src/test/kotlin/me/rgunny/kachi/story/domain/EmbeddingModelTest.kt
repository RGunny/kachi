package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("EmbeddingModel")
class EmbeddingModelTest {

    @ParameterizedTest
    @EnumSource(EmbeddingModel::class)
    @DisplayName("code로 되찾는다")
    fun ofCode(model: EmbeddingModel) {
        assertEquals(model, EmbeddingModel.ofCode(model.code))
    }

    @Test
    @DisplayName("모르는 code는 거부한다")
    fun rejectUnknownCode() {
        assertFailsWith<IllegalArgumentException> { EmbeddingModel.ofCode("unknown") }
    }

    @ParameterizedTest
    @EnumSource(EmbeddingModel::class)
    @DisplayName("차원은 양수다")
    fun positiveDimension(model: EmbeddingModel) {
        assert(model.dimension > 0)
    }
}
