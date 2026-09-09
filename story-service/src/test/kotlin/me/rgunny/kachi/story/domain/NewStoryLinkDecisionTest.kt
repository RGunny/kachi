package me.rgunny.kachi.story.domain

import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import me.rgunny.kachi.story.fixture.StoryTestFixture.STORY_ID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("NewStoryLinkDecision")
class NewStoryLinkDecisionTest {

    @Test
    @DisplayName("후보가 없으면 유사도도 없다")
    fun noCandidate() {
        val decision = NewStoryLinkDecision(candidateStoryId = null, similarity = null)

        assertFalse(decision.merged)
        assertNull(decision.candidateStoryId)
    }

    @Test
    @DisplayName("후보와 유사도는 함께 있어야 한다")
    fun candidateAndSimilarityTogether() {
        assertFailsWith<IllegalArgumentException> { NewStoryLinkDecision(STORY_ID, null) }
        assertFailsWith<IllegalArgumentException> { NewStoryLinkDecision(null, 0.5) }
    }
}
