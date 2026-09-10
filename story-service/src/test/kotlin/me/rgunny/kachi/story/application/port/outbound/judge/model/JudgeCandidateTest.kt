package me.rgunny.kachi.story.application.port.outbound.judge.model

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.domain.EmbeddingText
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JudgeCandidate")
class JudgeCandidateTest {

    @Test
    @DisplayName("텍스트와 코사인 유사도를 갖는다")
    fun holdTextAndSimilarity() {
        val candidate = JudgeCandidate(text = EmbeddingText.of("제목", "발췌문"), similarity = 0.65)

        assertEquals(0.65, candidate.similarity)
        assertEquals("제목\n발췌문", candidate.text.value)
    }

    @Test
    @DisplayName("코사인 범위를 벗어난 유사도는 거부한다")
    fun rejectOutOfRange() {
        assertFailsWith<IllegalArgumentException> { JudgeCandidate(EmbeddingText.of("a", "b"), 1.5) }
        assertFailsWith<IllegalArgumentException> { JudgeCandidate(EmbeddingText.of("a", "b"), -1.5) }
    }
}
