package me.rgunny.kachi.ai.application.service.story

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.StorySummaryPrompt
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StorySummaryPolicy")
class StorySummaryPolicyTest {

    private val now = AiTestFixture.NOW

    @Test
    @DisplayName("값의 하한과 상한을 검증한다")
    fun validateBounds() {
        assertFailsWith<IllegalArgumentException> { AiTestFixture.storySummaryPolicy(minNewArticles = 0) }
        assertFailsWith<IllegalArgumentException> { AiTestFixture.storySummaryPolicy(maxWait = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storySummaryPolicy(maxArticlesPerVersion = StorySummaryPrompt.MAX_ARTICLES + 1)
        }
    }

    @Test
    @DisplayName("미요약이 임계에 닿은 살아 있는 story만 즉시 요약 대상이다")
    fun requireImmediateSummaryAtThreshold() {
        val policy = AiTestFixture.storySummaryPolicy(minNewArticles = 2)
        val one = AiTestFixture.aiStory()
        val two = one.accept(listOf(AiKeyword.of("NVIDIA")), 2, now, now)

        assertFalse(policy.requiresImmediateSummary(one))
        assertTrue(policy.requiresImmediateSummary(two))
        assertFalse(policy.requiresImmediateSummary(two.mergeInto(AiTestFixture.OTHER_STORY_ID, now)))
    }

    @Test
    @DisplayName("기준 시각이 maxWait를 넘긴 미요약 story만 due다")
    fun dueOnlyAfterMaxWait() {
        val policy = AiTestFixture.storySummaryPolicy(maxWait = Duration.ofMinutes(60))

        assertEquals(now.minus(Duration.ofMinutes(60)), policy.dueThreshold(now))
        assertTrue(policy.isDue(AiTestFixture.aiStory(attachedAt = now.minus(Duration.ofMinutes(61))), now))
        assertFalse(policy.isDue(AiTestFixture.aiStory(attachedAt = now.minus(Duration.ofMinutes(59))), now))
    }

    @Test
    @DisplayName("요약이 있는 story는 마지막 버전 시각이 기준이다")
    fun dueBaselineIsLatestVersionAt() {
        val policy = AiTestFixture.storySummaryPolicy(maxWait = Duration.ofMinutes(60))
        val summarized = AiTestFixture.aiStory(attachedAt = now.minus(Duration.ofHours(3)))
            .summarized(1, null, now.minus(Duration.ofMinutes(30)))
            .accept(listOf(AiKeyword.of("NVIDIA")), 2, now.minus(Duration.ofHours(2)), now)

        assertFalse(policy.isDue(summarized, now))
    }
}
