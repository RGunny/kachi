package me.rgunny.kachi.story.adapter.outbound.judge

import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.application.port.outbound.judge.model.JudgeCandidate
import me.rgunny.kachi.story.domain.EmbeddingText
import me.rgunny.kachi.story.domain.StoryJudge
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ThresholdOnlyStoryLinkJudge")
class ThresholdOnlyStoryLinkJudgeTest {
    private val judge = ThresholdOnlyStoryLinkJudge()

    @Test
    @DisplayName("후보의 코사인 유사도를 그 순서대로 점수로 돌려준다")
    fun returnSimilarity() = runBlocking {
        val candidates = listOf(
            JudgeCandidate(EmbeddingText.of("a", "b"), 0.61),
            JudgeCandidate(EmbeddingText.of("c", "d"), 0.68)
        )

        assertEquals(listOf(0.61, 0.68), judge.score(EmbeddingText.of("q", "r"), candidates))
    }

    @Test
    @DisplayName("후보가 비면 빈 목록이다")
    fun emptyCandidates() = runBlocking {
        assertEquals(emptyList(), judge.score(EmbeddingText.of("q", "r"), emptyList()))
    }

    @Test
    @DisplayName("판정기 정체는 코사인만이다")
    fun identity() {
        assertEquals(StoryJudge.THRESHOLD_ONLY, judge.judge)
    }
}
