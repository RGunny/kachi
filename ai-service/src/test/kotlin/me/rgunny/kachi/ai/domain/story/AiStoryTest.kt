package me.rgunny.kachi.ai.domain.story

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AiStory")
class AiStoryTest {

    private val now = AiTestFixture.NOW

    @Nested
    @DisplayName("open()")
    inner class Open {

        @Test
        @DisplayName("첫 기사로 미요약 1건짜리 상태를 연다")
        fun openWithFirstArticle() {
            val story = AiTestFixture.aiStory(attachedAt = now)

            assertEquals(1, story.pendingCount)
            assertEquals(now, story.oldestPendingAt)
            assertEquals(0, story.latestVersion)
            assertNull(story.latestVersionAt)
            assertEquals(1, story.version)
            assertEquals(1, story.nextVersion)
            assertEquals(now, story.summaryWaitBaseline)
        }

        @Test
        @DisplayName("키워드 없는 story는 열 수 없다")
        fun rejectEmptyKeywords() {
            assertFailsWith<IllegalArgumentException> {
                AiTestFixture.aiStory(keywords = emptyList())
            }
        }
    }

    @Nested
    @DisplayName("accept()")
    inner class Accept {

        @Test
        @DisplayName("스냅샷 키워드를 합집합으로 더하고 미요약 수를 올린다")
        fun acceptUnionsKeywordsAndCountsPending() {
            val story = AiTestFixture.aiStory(keywords = listOf("NVIDIA"), articleCount = 1, attachedAt = now)

            val accepted = story.accept(
                keywords = listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("GPU")),
                articleCount = 2,
                attachedAt = now.plusSeconds(60),
                now = now.plusSeconds(60)
            )

            assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("GPU")), accepted.keywords)
            assertEquals(2, accepted.articleCount)
            assertEquals(2, accepted.pendingCount)
            assertEquals(now, accepted.oldestPendingAt)
            assertEquals(story.version + 1, accepted.version)
        }

        @Test
        @DisplayName("먼저 붙은 기사가 늦게 도착해도 기준 시각은 더 이른 값을 지킨다")
        fun keepEarliestPendingAt() {
            val story = AiTestFixture.aiStory(attachedAt = now)

            val accepted = story.accept(
                keywords = listOf(AiKeyword.of("NVIDIA")),
                articleCount = 2,
                attachedAt = now.minusSeconds(120),
                now = now
            )

            assertEquals(now.minusSeconds(120), accepted.oldestPendingAt)
        }

        @Test
        @DisplayName("흡수된 story는 기사를 받을 수 없다")
        fun rejectMergedStory() {
            val merged = AiTestFixture.aiStory().mergeInto(AiTestFixture.OTHER_STORY_ID, now)

            assertFailsWith<IllegalArgumentException> {
                merged.accept(listOf(AiKeyword.of("NVIDIA")), 2, now, now)
            }
        }
    }

    @Nested
    @DisplayName("summarized()")
    inner class Summarized {

        @Test
        @DisplayName("전부 요약되면 미요약이 0이 되고 버전이 오른다")
        fun summarizeAllPending() {
            val story = AiTestFixture.aiStory(attachedAt = now)

            val summarized = story.summarized(
                summarizedCount = 1,
                remainingOldestPendingAt = null,
                now = now.plusSeconds(10)
            )

            assertEquals(1, summarized.latestVersion)
            assertEquals(now.plusSeconds(10), summarized.latestVersionAt)
            assertEquals(0, summarized.pendingCount)
            assertNull(summarized.oldestPendingAt)
            assertEquals(now.plusSeconds(10), summarized.summaryWaitBaseline)
        }

        @Test
        @DisplayName("일부만 요약되면 남은 기사의 기준 시각이 필요하다")
        fun keepRemainingPending() {
            val story = AiTestFixture.aiStory(attachedAt = now)
                .accept(listOf(AiKeyword.of("NVIDIA")), 2, now.plusSeconds(30), now.plusSeconds(30))

            val summarized = story.summarized(
                summarizedCount = 1,
                remainingOldestPendingAt = now.plusSeconds(30),
                now = now.plusSeconds(60)
            )

            assertEquals(1, summarized.pendingCount)
            assertEquals(now.plusSeconds(30), summarized.oldestPendingAt)
        }

        @Test
        @DisplayName("남은 수와 기준 시각이 어긋나면 거부한다")
        fun rejectInconsistentRemaining() {
            val story = AiTestFixture.aiStory(attachedAt = now)

            assertFailsWith<IllegalArgumentException> {
                story.summarized(summarizedCount = 1, remainingOldestPendingAt = now, now = now)
            }
        }

        @Test
        @DisplayName("미요약 수를 넘는 요약은 거부한다")
        fun rejectOverCount() {
            val story = AiTestFixture.aiStory(attachedAt = now)

            assertFailsWith<IllegalArgumentException> {
                story.summarized(summarizedCount = 2, remainingOldestPendingAt = null, now = now)
            }
        }
    }

    @Nested
    @DisplayName("mergeInto()와 absorb()")
    inner class Merge {

        @Test
        @DisplayName("흡수된 쪽은 미요약이 비워지고 흡수한 쪽이 넘겨받는다")
        fun mergeMovesPending() {
            val absorbed = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID, keywords = listOf("tesla"), attachedAt = now)
            val absorbing = AiTestFixture.aiStory(attachedAt = now.plusSeconds(60))

            val mergedAbsorbed = absorbed.mergeInto(absorbing.storyId, now.plusSeconds(90))
            val mergedAbsorbing = absorbing.absorb(
                movedPendingCount = absorbed.pendingCount,
                movedOldestPendingAt = absorbed.oldestPendingAt,
                mergedKeywords = absorbed.keywords,
                now = now.plusSeconds(90)
            )

            assertTrue(mergedAbsorbed.merged)
            assertEquals(0, mergedAbsorbed.pendingCount)
            assertNull(mergedAbsorbed.oldestPendingAt)
            assertEquals(2, mergedAbsorbing.pendingCount)
            assertEquals(now, mergedAbsorbing.oldestPendingAt)
            assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("tesla")), mergedAbsorbing.keywords)
        }

        @Test
        @DisplayName("자기 자신에게는 흡수될 수 없다")
        fun rejectSelfMerge() {
            val story = AiTestFixture.aiStory()

            assertFailsWith<IllegalArgumentException> { story.mergeInto(story.storyId, now) }
        }

        @Test
        @DisplayName("자리표시는 흡수 사실만 갖고 미요약이 없다")
        fun trackMergedPlaceholder() {
            val placeholder = AiStory.trackMerged(
                storyId = AiTestFixture.OTHER_STORY_ID,
                mergedInto = AiTestFixture.STORY_ID,
                now = now
            )

            assertTrue(placeholder.merged)
            assertEquals(0, placeholder.pendingCount)
            assertEquals(AiTestFixture.STORY_ID, placeholder.mergedInto)
        }
    }

    @Test
    @DisplayName("기준 시각은 마지막 버전 시각이 있으면 그 값, 없으면 첫 미요약 시각이다")
    fun summaryWaitBaseline() {
        val opened = AiTestFixture.aiStory(attachedAt = now)
        assertEquals(now, opened.summaryWaitBaseline)

        val summarized = opened.summarized(1, null, now.plus(Duration.ofMinutes(5)))
        assertEquals(now.plus(Duration.ofMinutes(5)), summarized.summaryWaitBaseline)
    }
}
