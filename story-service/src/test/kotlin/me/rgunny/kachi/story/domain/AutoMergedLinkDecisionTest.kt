package me.rgunny.kachi.story.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.rgunny.kachi.story.fixture.StoryTestFixture.STORY_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AutoMergedLinkDecision")
class AutoMergedLinkDecisionTest {

    @Test
    @DisplayName("붙인 판정이고 후보가 곧 소속이다")
    fun mergedToCandidate() {
        val decision = AutoMergedLinkDecision(storyId = STORY_ID, similarity = 0.82)

        assertTrue(decision.merged)
        assertEquals(STORY_ID, decision.candidateStoryId)
        assertEquals(0.82, decision.similarity)
    }

    @Test
    @DisplayName("코사인 범위 밖 유사도는 거부한다")
    fun rejectOutOfRange() {
        assertFailsWith<IllegalArgumentException> { AutoMergedLinkDecision(STORY_ID, 1.2) }
    }
}
