package me.rgunny.kachi.ai.adapter.outbound.persistence.summary

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryArticleMongoDocument
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryArticlePersistenceAdapter
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryMongoDocument
import me.rgunny.kachi.ai.adapter.outbound.persistence.story.AiStoryPersistenceAdapter
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.AiOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.query.Query

@DisplayName("StorySummaryPersistenceAdapter 통합 테스트")
class StorySummaryPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: StorySummaryPersistenceAdapter

    @Autowired
    private lateinit var storyAdapter: AiStoryPersistenceAdapter

    @Autowired
    private lateinit var articleAdapter: AiStoryArticlePersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val outboxes: AiOutboxCollection by lazy { AiOutboxCollection(mongoTemplate) }

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), StorySummaryMongoDocument::class.java).block()
        mongoTemplate.remove(Query(), AiStoryMongoDocument::class.java).block()
        mongoTemplate.remove(Query(), AiStoryArticleMongoDocument::class.java).block()
        outboxes.clear()
        // unique index 직접 생성(reactive 자동 index 생성은 비동기)
        mongoTemplate.indexOps(StorySummaryMongoDocument::class.java)
            .createIndex(
                Index().on("storyId", Sort.Direction.ASC).on("version", Sort.Direction.DESC)
                    .named("ux_story_summaries_story_version").unique()
            )
            .block()
    }

    private fun seedStoryWithArticle(): AiStory = runBlocking {
        val story = AiTestFixture.aiStory()
        articleAdapter.openStory(story, AiTestFixture.storyArticle())

        story
    }

    @Test
    @DisplayName("버전·기사 마킹·story 전이·outbox를 한 트랜잭션으로 쓴다")
    fun saveVersionAtomically() = runBlocking {
        val story = seedStoryWithArticle()
        val summary = AiTestFixture.storySummary()

        val saved = adapter.saveVersion(
            summary = summary,
            story = story.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = story.version,
            outboxes = listOf(AiTestFixture.outbox(eventKey = "${summary.storyId.value}:1"))
        )

        assertTrue(saved)
        assertEquals(1, adapter.findLatest(AiTestFixture.STORY_ID)?.version)
        assertEquals(0, storyAdapter.findByStoryId(AiTestFixture.STORY_ID)?.pendingCount)
        assertTrue(articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).isEmpty())
        assertEquals(1, outboxes.findAll().size)
    }

    @Test
    @DisplayName("같은 (storyId, version)이 이미 있으면 아무것도 쓰지 않는다")
    fun rejectDuplicateVersion() = runBlocking {
        val story = seedStoryWithArticle()
        val first = adapter.saveVersion(
            summary = AiTestFixture.storySummary(),
            story = story.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = story.version,
            outboxes = emptyList()
        )
        assertTrue(first)
        articleAdapter.attach(
            article = AiTestFixture.storyArticle(newsId = UUID.fromString("018f0000-0000-7000-8000-000000000601")),
            story = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)!!
                .accept(story.keywords, 2, AiTestFixture.NOW, AiTestFixture.NOW),
            expectedVersion = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)!!.version
        )
        val current = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)!!

        val second = adapter.saveVersion(
            summary = AiTestFixture.storySummary(
                newNewsIds = listOf(UUID.fromString("018f0000-0000-7000-8000-000000000601"))
            ),
            story = current.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = current.version,
            outboxes = emptyList()
        )

        assertFalse(second)
        assertEquals(1, articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).size)
    }

    @Test
    @DisplayName("story CAS가 어긋나면 요약과 마킹까지 되돌린다")
    fun rollbackOnStoryCasMiss() = runBlocking {
        val story = seedStoryWithArticle()

        val saved = adapter.saveVersion(
            summary = AiTestFixture.storySummary(),
            story = story.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = story.version + 7,
            outboxes = emptyList()
        )

        assertFalse(saved)
        assertTrue(adapter.findByStory(AiTestFixture.STORY_ID, 10).isEmpty())
        assertEquals(1, articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).size)
    }

    @Test
    @DisplayName("이미 마킹된 기사가 섞이면 아무것도 쓰지 않는다")
    fun rollbackOnMarkingMismatch() = runBlocking {
        val story = seedStoryWithArticle()
        val first = adapter.saveVersion(
            summary = AiTestFixture.storySummary(),
            story = story.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = story.version,
            outboxes = emptyList()
        )
        assertTrue(first)
        val current = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)!!

        // 마킹 수가 어긋나는 v2(같은 기사 NEWS_ID 재수록)
        val second = adapter.saveVersion(
            summary = AiTestFixture.storySummary(version = 2, sourceNewsCount = 2),
            story = AiStory.restore(
                storyId = current.storyId,
                keywords = current.keywords,
                articleCount = current.articleCount,
                latestVersion = 2,
                latestVersionAt = AiTestFixture.NOW,
                pendingCount = 0,
                oldestPendingAt = null,
                mergedInto = null,
                version = current.version + 1,
                updatedAt = AiTestFixture.NOW
            ),
            expectedStoryVersion = current.version,
            outboxes = emptyList()
        )

        assertFalse(second)
        assertEquals(1, adapter.findByStory(AiTestFixture.STORY_ID, 10).size)
    }

    @Test
    @DisplayName("버전을 최신 순으로 조회한다")
    fun findLatestFirst() = runBlocking {
        val story = seedStoryWithArticle()
        adapter.saveVersion(
            summary = AiTestFixture.storySummary(),
            story = story.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = story.version,
            outboxes = emptyList()
        )
        val newsId2 = UUID.fromString("018f0000-0000-7000-8000-000000000602")
        val v1 = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)!!
        val accepted = v1.accept(v1.keywords, 2, AiTestFixture.NOW, AiTestFixture.NOW)
        articleAdapter.attach(AiTestFixture.storyArticle(newsId = newsId2), accepted, v1.version)
        adapter.saveVersion(
            summary = AiTestFixture.storySummary(version = 2, newNewsIds = listOf(newsId2), sourceNewsCount = 2),
            story = accepted.summarized(1, null, AiTestFixture.NOW),
            expectedStoryVersion = accepted.version,
            outboxes = emptyList()
        )

        assertEquals(listOf(2L, 1L), adapter.findByStory(AiTestFixture.STORY_ID, 10).map { it.version })
        assertEquals(2L, adapter.findLatest(AiTestFixture.STORY_ID)?.version)
    }
}
