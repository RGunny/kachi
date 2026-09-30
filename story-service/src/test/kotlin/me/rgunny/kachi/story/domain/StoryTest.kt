package me.rgunny.kachi.story.domain

import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.rgunny.kachi.story.fixture.StoryTestFixture.NOW
import me.rgunny.kachi.story.fixture.StoryTestFixture.STORY_ID
import me.rgunny.kachi.story.fixture.StoryTestFixture.article
import me.rgunny.kachi.story.fixture.StoryTestFixture.embedding
import me.rgunny.kachi.story.fixture.StoryTestFixture.story
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Story")
class StoryTest {
    private val otherStoryId = StoryId.of(UUID.fromString("018f0000-0000-7000-8000-000000000002"))
    private val otherNewsId = NewsId.of(UUID.fromString("018f0000-0000-7000-8000-000000000010"))

    @Nested
    @DisplayName("open()")
    inner class Open {

        @Test
        @DisplayName("첫 기사의 소속·임베딩·키워드·발행 시각으로 연다")
        fun openWithFirstArticle() {
            val first = article(keywords = listOf("nvidia", "ai"), publishedAt = NOW.minus(Duration.ofHours(3)))

            val story = Story.open(first, NOW)

            assertEquals(first.storyId, story.id)
            assertEquals(StoryStatus.OPEN, story.status)
            assertEquals(first.embedding, story.centroid)
            assertEquals(1, story.articleCount)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("ai")), story.keywords)
            assertEquals(NOW, story.openedAt)
            assertEquals(first.publishedAt, story.lastArticleAt)
            assertNull(story.closedAt)
            assertNull(story.parentStoryId)
            assertNull(story.mergedInto)
            assertEquals(0, story.version)
        }

        @Test
        @DisplayName("자기 자신을 부모로 둘 수 없다")
        fun rejectSelfParent() {
            assertFailsWith<IllegalArgumentException> { Story.open(article(), NOW, parentStoryId = STORY_ID) }
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("CLOSED가 아닌데 closedAt이 있거나 mergedInto가 있으면 거부한다")
        fun rejectInconsistentState() {
            assertFailsWith<IllegalArgumentException> { restore(status = StoryStatus.OPEN, closedAt = NOW) }
            assertFailsWith<IllegalArgumentException> { restore(status = StoryStatus.CLOSED, closedAt = null) }
            assertFailsWith<IllegalArgumentException> { restore(status = StoryStatus.OPEN, mergedInto = otherStoryId) }
        }

        @Test
        @DisplayName("기사 수 0, 빈 키워드, 음수 version은 거부한다")
        fun rejectInvalidCounts() {
            assertFailsWith<IllegalArgumentException> { restore(articleCount = 0) }
            assertFailsWith<IllegalArgumentException> { restore(keywords = emptySet()) }
            assertFailsWith<IllegalArgumentException> { restore(version = -1) }
        }

        private fun restore(
            status: StoryStatus = StoryStatus.OPEN,
            articleCount: Int = 1,
            keywords: Set<StoryKeyword> = setOf(StoryKeyword.of("nvidia")),
            closedAt: java.time.Instant? = null,
            mergedInto: StoryId? = null,
            version: Long = 0
        ): Story {
            return Story.restore(
                id = STORY_ID,
                status = status,
                centroid = embedding(1f),
                articleCount = articleCount,
                keywords = keywords,
                openedAt = NOW,
                lastArticleAt = NOW,
                closedAt = closedAt,
                parentStoryId = null,
                mergedInto = mergedInto,
                version = version
            )
        }
    }

    @Nested
    @DisplayName("attach()")
    inner class Attach {

        @Test
        @DisplayName("평균·기사 수·키워드·마지막 발행 시각·version이 함께 움직인다")
        fun attachArticle() {
            val story = story(article(embedding = embedding(1f, 0f), publishedAt = NOW.minus(Duration.ofHours(2))))
            val later = article(
                newsId = otherNewsId,
                embedding = embedding(0f, 1f),
                keywords = listOf("ai"),
                publishedAt = NOW.minus(Duration.ofHours(1))
            )

            val attached = story.attach(later, NOW)

            assertEquals(embedding(0.5f, 0.5f), attached.centroid)
            assertEquals(2, attached.articleCount)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("ai")), attached.keywords)
            assertEquals(later.publishedAt, attached.lastArticleAt)
            assertEquals(1, attached.version)
        }

        @Test
        @DisplayName("더 이른 기사가 붙어도 마지막 발행 시각은 뒤로 가지 않는다")
        fun keepLatestPublishedAt() {
            val story = story(article(publishedAt = NOW))
            val earlier = article(newsId = otherNewsId, publishedAt = NOW.minus(Duration.ofDays(1)))

            assertEquals(NOW, story.attach(earlier, NOW).lastArticleAt)
        }

        @Test
        @DisplayName("다른 story의 기사는 거부한다")
        fun rejectForeignArticle() {
            assertFailsWith<IllegalArgumentException> { story().attach(article(storyId = otherStoryId), NOW) }
        }

        @Test
        @DisplayName("닫힌 story에는 붙일 수 없다")
        fun rejectWhenClosed() {
            assertFailsWith<IllegalStateException> { story().close(NOW).attach(article(newsId = otherNewsId), NOW) }
        }
    }

    @Nested
    @DisplayName("acceptsMore()")
    inner class AcceptsMore {

        @Test
        @DisplayName("열려 있고 상한 아래일 때만 받는다")
        fun openAndBelowMax() {
            val story = story()

            assertTrue(story.acceptsMore(2))
            assertFalse(story.acceptsMore(1))
            assertFalse(story.close(NOW).acceptsMore(2))
        }
    }

    @Nested
    @DisplayName("close()")
    inner class Close {

        @Test
        @DisplayName("CLOSED로 바꾸고 시각과 version을 남긴다")
        fun closeStory() {
            val closed = story().close(NOW)

            assertEquals(StoryStatus.CLOSED, closed.status)
            assertEquals(NOW, closed.closedAt)
            assertEquals(1, closed.version)
        }

        @Test
        @DisplayName("두 번 닫을 수 없다")
        fun rejectDoubleClose() {
            assertFailsWith<IllegalStateException> { story().close(NOW).close(NOW) }
        }
    }

    @Nested
    @DisplayName("recompose()")
    inner class Recompose {

        @Test
        @DisplayName("centroid 평균·기사 수·키워드 합집합·마지막 발행 시각을 다시 계산한다")
        fun recomputeDerivedState() {
            val first = article(embedding = embedding(1f, 0f), publishedAt = NOW.minus(Duration.ofHours(2)))
            val second = article(
                newsId = otherNewsId,
                embedding = embedding(0f, 1f),
                keywords = listOf("ai"),
                publishedAt = NOW.minus(Duration.ofHours(1))
            )
            val story = story(first)

            val recomposed = story.recompose(listOf(first, second), NOW)

            assertEquals(embedding(0.5f, 0.5f), recomposed.centroid)
            assertEquals(2, recomposed.articleCount)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("ai")), recomposed.keywords)
            assertEquals(second.publishedAt, recomposed.lastArticleAt)
            assertEquals(story.version + 1, recomposed.version)
        }

        @Test
        @DisplayName("빈 목록과 다른 story의 기사는 거부한다")
        fun rejectInvalidArticles() {
            assertFailsWith<IllegalArgumentException> { story().recompose(emptyList(), NOW) }
            assertFailsWith<IllegalArgumentException> {
                story().recompose(listOf(article(newsId = otherNewsId, storyId = otherStoryId)), NOW)
            }
        }

        @Test
        @DisplayName("닫힌 story는 재구성할 수 없다")
        fun rejectWhenClosed() {
            assertFailsWith<IllegalStateException> { story().close(NOW).recompose(listOf(article()), NOW) }
        }
    }

    @Nested
    @DisplayName("mergeInto() · absorb()")
    inner class Merge {
        private val target = story(article(embedding = embedding(1f, 0f), publishedAt = NOW.minus(Duration.ofHours(5))))
        private val other = story(
            article(
                newsId = otherNewsId,
                storyId = otherStoryId,
                embedding = embedding(0f, 1f),
                keywords = listOf("ai"),
                publishedAt = NOW.minus(Duration.ofHours(1))
            )
        ).attach(
            article(
                newsId = NewsId.of(UUID.randomUUID()),
                storyId = otherStoryId,
                embedding = embedding(0f, 1f),
                keywords = listOf("ai")
            ),
            NOW
        )

        @Test
        @DisplayName("흡수된 쪽은 닫히고 어디로 갔는지 남긴다")
        fun mergedSideIsClosed() {
            val merged = other.mergeInto(target, NOW)

            assertEquals(StoryStatus.CLOSED, merged.status)
            assertEquals(target.id, merged.mergedInto)
            assertEquals(NOW, merged.closedAt)
        }

        @Test
        @DisplayName("흡수한 쪽은 기사 수로 가중한 평균과 합집합 키워드를 갖는다")
        fun absorbingSideAggregates() {
            val absorbed = target.absorb(other, NOW)

            assertEquals(embedding(1f / 3, 2f / 3), absorbed.centroid)
            assertEquals(3, absorbed.articleCount)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("ai")), absorbed.keywords)
            assertEquals(other.lastArticleAt, absorbed.lastArticleAt)
            assertEquals(StoryStatus.OPEN, absorbed.status)
        }

        @Test
        @DisplayName("자기 자신이나 닫힌 story와는 합칠 수 없다")
        fun rejectSelfOrClosed() {
            assertFailsWith<IllegalArgumentException> { target.absorb(target, NOW) }
            assertFailsWith<IllegalArgumentException> { target.absorb(other.close(NOW), NOW) }
            assertFailsWith<IllegalArgumentException> { other.mergeInto(target.close(NOW), NOW) }
            assertFailsWith<IllegalStateException> { other.close(NOW).mergeInto(target, NOW) }
        }
    }
}
