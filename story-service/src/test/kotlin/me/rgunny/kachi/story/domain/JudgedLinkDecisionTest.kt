package me.rgunny.kachi.story.domain

import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import me.rgunny.kachi.story.fixture.StoryTestFixture.STORY_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("JudgedLinkDecision")
class JudgedLinkDecisionTest {

    @Test
    @DisplayName("judge가 낮게 봐 붙이지 않은 기록도 후보를 남긴다")
    fun keepCandidateWhenNotMerged() {
        val decision = JudgedLinkDecision(
            candidateStoryId = STORY_ID,
            similarity = 0.65,
            judge = StoryJudge.BGE_RERANKER_V2_M3,
            judgeScore = 0.05,
            merged = false
        )

        assertFalse(decision.merged)
        assert(decision.candidateStoryId == STORY_ID)
    }

    @Test
    @DisplayName("판정 점수는 0과 1 사이여야 한다")
    fun rejectJudgeScoreOutOfRange() {
        assertFailsWith<IllegalArgumentException> {
            JudgedLinkDecision(STORY_ID, 0.65, StoryJudge.BGE_RERANKER_V2_M3, 1.5, true)
        }
    }
}
