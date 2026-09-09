package me.rgunny.kachi.story.adapter.outbound.persistence.story

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.domain.EmbeddingModel
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("EmbeddingBinary")
class EmbeddingBinaryTest {

    @Test
    @DisplayName("값 하나가 float32 little-endian 4바이트로 놓인다")
    fun encodeLittleEndian() {
        val bytes = EmbeddingBinary.encode(StoryTestFixture.embedding(1.0f))

        assertEquals(EmbeddingModel.BGE_M3.dimension * Float.SIZE_BYTES, bytes.size)
        assertContentEquals(byteArrayOf(0x00, 0x00, 0x80.toByte(), 0x3F), bytes.copyOfRange(0, 4))
    }

    @Test
    @DisplayName("encode한 바이트열을 decode하면 같은 벡터다")
    fun roundTrip() {
        val embedding = StoryTestFixture.embedding(0.1f, -2.5f, 3.75f)

        val decoded = EmbeddingBinary.decode(EmbeddingModel.BGE_M3, EmbeddingBinary.encode(embedding))

        assertEquals(embedding, decoded)
    }

    @Test
    @DisplayName("모델 차원과 맞지 않는 길이는 거부한다")
    fun rejectWrongLength() {
        assertFailsWith<IllegalArgumentException> {
            EmbeddingBinary.decode(EmbeddingModel.BGE_M3, ByteArray(8))
        }
    }
}
