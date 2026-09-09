package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.story.domain.Embedding
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.domain.StoryStatus
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@DisplayName("StoryPersistenceAdapter 통합 테스트")
class StoryPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: StoryPersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val now = StoryTestFixture.NOW

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(StoryMongoDocument::class.java).all().block()
    }

    @Nested
    @DisplayName("save() / findById()")
    inner class SaveAndFindById {

        @Test
        @DisplayName("OPEN story의 전 필드를 저장하고 그대로 복원한다")
        fun saveAndRestoreOpenStory() = runBlocking {
            val parent = StoryId.newId()
            val story = story(
                keywords = listOf("nvidia", "엔비디아"),
                embedding = StoryTestFixture.embedding(0.5f, -0.25f),
                publishedAt = now.minus(Duration.ofHours(3)),
                parentStoryId = parent
            )

            adapter.save(story)
            val found = adapter.findById(story.id)

            assertNotNull(found)
            assertEquals(story.id, found.id)
            assertEquals(StoryStatus.OPEN, found.status)
            assertEquals(story.centroid, found.centroid)
            assertEquals(1, found.articleCount)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("엔비디아")), found.keywords)
            assertEquals(now, found.openedAt)
            assertEquals(now.minus(Duration.ofHours(3)), found.lastArticleAt)
            assertNull(found.closedAt)
            assertEquals(parent, found.parentStoryId)
            assertNull(found.mergedInto)
            assertEquals(0, found.version)
        }

        @Test
        @DisplayName("흡수된 story는 CLOSED·closedAt·mergedInto가 함께 복원된다")
        fun saveAndRestoreMergedStory() = runBlocking {
            val target = story()
            val merged = story().mergeInto(target, now.plusSeconds(10))

            adapter.save(merged)
            val found = adapter.findById(merged.id)

            assertNotNull(found)
            assertEquals(StoryStatus.CLOSED, found.status)
            assertEquals(now.plusSeconds(10), found.closedAt)
            assertEquals(target.id, found.mergedInto)
            assertEquals(1, found.version)
        }

        @Test
        @DisplayName("없는 id를 조회하면 null이다")
        fun findByIdReturnsNullWhenAbsent() = runBlocking {
            assertNull(adapter.findById(StoryId.newId()))
        }
    }

    @Nested
    @DisplayName("findByIds()")
    inner class FindByIds {

        @Test
        @DisplayName("요청한 id의 story만 읽는다")
        fun findOnlyRequestedIds() = runBlocking {
            val first = adapter.save(story())
            val second = adapter.save(story())
            adapter.save(story())

            val found = adapter.findByIds(listOf(first.id, second.id, StoryId.newId()))

            assertEquals(setOf(first.id, second.id), found.map { it.id }.toSet())
        }

        @Test
        @DisplayName("id가 비어 있으면 빈 목록이다")
        fun returnEmptyWhenNoIds() = runBlocking {
            adapter.save(story())

            assertTrue(adapter.findByIds(emptyList()).isEmpty())
        }
    }

    @Nested
    @DisplayName("update()")
    inner class Update {

        @Test
        @DisplayName("저장된 version이 기대값이면 전이 결과를 쓴다")
        fun updateWithMatchingVersion() = runBlocking {
            val story = adapter.save(story(embedding = StoryTestFixture.embedding(1f, 0f)))
            val attached = story.attach(
                article(storyId = story.id, keywords = listOf("nvidia", "gpu"), embedding = StoryTestFixture.embedding(0f, 1f)),
                now.plusSeconds(5)
            )

            val updated = adapter.update(attached, expectedVersion = 0)

            assertTrue(updated)
            val found = assertNotNull(adapter.findById(story.id))
            assertEquals(1, found.version)
            assertEquals(2, found.articleCount)
            assertEquals(StoryTestFixture.embedding(0.5f, 0.5f), found.centroid)
            assertEquals(setOf(StoryKeyword.of("nvidia"), StoryKeyword.of("gpu")), found.keywords)
        }

        @Test
        @DisplayName("version이 어긋나면 쓰지 않고 문서를 그대로 둔다")
        fun rejectUpdateWithStaleVersion() = runBlocking {
            val story = adapter.save(story())
            val closed = story.close(now.plusSeconds(5))

            assertFalse(adapter.update(closed, expectedVersion = 3))

            val found = assertNotNull(adapter.findById(story.id))
            assertEquals(StoryStatus.OPEN, found.status)
            assertEquals(0, found.version)
        }

        @Test
        @DisplayName("같은 version에서 출발한 두 전이는 한쪽만 쓴다")
        fun onlyOneUpdateSucceeds() = runBlocking {
            val story = adapter.save(story())

            val first = adapter.update(story.close(now), expectedVersion = 0)
            val second = adapter.update(story.close(now.plusSeconds(1)), expectedVersion = 0)

            assertTrue(first)
            assertFalse(second)
            assertEquals(now, adapter.findById(story.id)?.closedAt)
        }
    }

    @Nested
    @DisplayName("findOpenWithLastArticleBefore()")
    inner class FindOpenWithLastArticleBefore {

        @Test
        @DisplayName("기준보다 오래된 OPEN story만 오래된 순으로 읽는다")
        fun findStaleOpenInOrder() = runBlocking {
            val oldest = adapter.save(story(publishedAt = now.minus(Duration.ofHours(3))))
            val stale = adapter.save(story(publishedAt = now.minus(Duration.ofHours(2))))
            adapter.save(story(publishedAt = now.minus(Duration.ofMinutes(30))))
            adapter.save(story(publishedAt = now.minus(Duration.ofHours(3))).close(now))

            val found = adapter.findOpenWithLastArticleBefore(threshold = now.minus(Duration.ofHours(1)), limit = 10)

            assertEquals(listOf(oldest.id, stale.id), found.map { it.id })
        }

        @Test
        @DisplayName("limit을 넘겨 읽지 않는다")
        fun limitResults() = runBlocking {
            adapter.save(story(publishedAt = now.minus(Duration.ofHours(3))))
            adapter.save(story(publishedAt = now.minus(Duration.ofHours(2))))

            assertEquals(1, adapter.findOpenWithLastArticleBefore(threshold = now, limit = 1).size)
        }
    }

    @Nested
    @DisplayName("find()")
    inner class Find {

        @Test
        @DisplayName("상태와 시작 시각으로 거르고 최근 순으로 읽는다")
        fun filterAndOrder() = runBlocking {
            val old = adapter.save(story(openedAt = now.minus(Duration.ofDays(2))))
            val recent = adapter.save(story(openedAt = now.minus(Duration.ofHours(1))))
            val newest = adapter.save(story(openedAt = now))
            val closed = adapter.save(story(openedAt = now.minus(Duration.ofHours(2))).close(now))

            assertEquals(listOf(newest.id, recent.id, closed.id, old.id), adapter.find(null, null, 10).map { it.id })
            assertEquals(listOf(closed.id), adapter.find(StoryStatus.CLOSED, null, 10).map { it.id })
            assertEquals(
                listOf(newest.id, recent.id, closed.id),
                adapter.find(null, now.minus(Duration.ofHours(2)), 10).map { it.id }
            )
            assertEquals(listOf(newest.id, recent.id), adapter.find(StoryStatus.OPEN, now.minus(Duration.ofHours(2)), 2).map { it.id })
        }
    }

    @Nested
    @DisplayName("index")
    inner class Indexes {

        @Test
        @DisplayName("collection에 선언한 index가 만들어진다")
        fun createDeclaredIndexes() {
            val indexNames = mongoTemplate.indexOps(StoryMongoDocument::class.java)
                .indexInfo
                .collectList()
                .block()
                .orEmpty()
                .map { it.name }

            assertTrue(
                indexNames.containsAll(listOf("ix_stories_status_last_article_at", "ix_stories_merged_into")),
                "생성된 index: $indexNames"
            )
        }
    }

    private fun article(
        storyId: StoryId,
        keywords: List<String> = listOf("nvidia"),
        embedding: Embedding = StoryTestFixture.embedding(1f, 0f),
        publishedAt: Instant = now.minus(Duration.ofHours(1))
    ) = StoryTestFixture.article(
        newsId = NewsId.of(UUID.randomUUID()),
        storyId = storyId,
        keywords = keywords,
        embedding = embedding,
        publishedAt = publishedAt
    )

    /** 매번 새 id로 연 story. */
    private fun story(
        keywords: List<String> = listOf("nvidia"),
        embedding: Embedding = StoryTestFixture.embedding(1f, 0f),
        publishedAt: Instant = now.minus(Duration.ofHours(1)),
        openedAt: Instant = now,
        parentStoryId: StoryId? = null
    ): Story {
        val first = article(storyId = StoryId.newId(), keywords = keywords, embedding = embedding, publishedAt = publishedAt)

        return Story.open(first, openedAt, parentStoryId)
    }
}
