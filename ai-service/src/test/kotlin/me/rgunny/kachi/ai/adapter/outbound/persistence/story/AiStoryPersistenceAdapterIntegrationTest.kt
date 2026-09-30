package me.rgunny.kachi.ai.adapter.outbound.persistence.story

import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query

@DisplayName("AiStoryPersistenceAdapter 통합 테스트")
class AiStoryPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var storyAdapter: AiStoryPersistenceAdapter

    @Autowired
    private lateinit var articleAdapter: AiStoryArticlePersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val now = AiTestFixture.NOW

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(Query(), AiStoryMongoDocument::class.java).block()
        mongoTemplate.remove(Query(), AiStoryArticleMongoDocument::class.java).block()
    }

    private fun newsId(index: Int): UUID = UUID.fromString("018f0000-0000-7000-8000-00000000050$index")

    @Nested
    @DisplayName("openStory()와 attach()")
    inner class Record {

        @Test
        @DisplayName("첫 기사와 story 상태를 함께 저장한다")
        fun openStoryWithFirstArticle() = runBlocking {
            val outcome = articleAdapter.openStory(AiTestFixture.aiStory(), AiTestFixture.storyArticle())

            assertEquals(RecordStoryArticleOutcome.RECORDED, outcome)
            assertEquals(1, storyAdapter.findByStoryId(AiTestFixture.STORY_ID)?.pendingCount)
            assertEquals(1, articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).size)
        }

        @Test
        @DisplayName("같은 newsId는 DUPLICATED, 있는 story의 open은 STORY_CHANGED다")
        fun classifyDuplicates() = runBlocking {
            articleAdapter.openStory(AiTestFixture.aiStory(), AiTestFixture.storyArticle())

            val duplicated = articleAdapter.openStory(
                AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID),
                AiTestFixture.storyArticle()
            )
            val storyChanged = articleAdapter.openStory(
                AiTestFixture.aiStory(),
                AiTestFixture.storyArticle(newsId = newsId(9))
            )

            assertEquals(RecordStoryArticleOutcome.DUPLICATED, duplicated)
            assertEquals(RecordStoryArticleOutcome.STORY_CHANGED, storyChanged)
        }

        @Test
        @DisplayName("attach는 story CAS가 어긋나면 기사 저장까지 되돌린다")
        fun rollbackArticleOnCasMiss() = runBlocking {
            val story = AiTestFixture.aiStory()
            articleAdapter.openStory(story, AiTestFixture.storyArticle())
            val accepted = story.accept(listOf(AiKeyword.of("NVIDIA")), 2, now, now)

            val outcome = articleAdapter.attach(
                article = AiTestFixture.storyArticle(newsId = newsId(1)),
                story = accepted,
                expectedVersion = story.version + 7
            )

            assertEquals(RecordStoryArticleOutcome.STORY_CHANGED, outcome)
            assertEquals(1, articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).size)
            assertEquals(story.version, storyAdapter.findByStoryId(AiTestFixture.STORY_ID)?.version)
        }

        @Test
        @DisplayName("attach가 성공하면 기사와 story 전이가 함께 저장된다")
        fun attachArticle() = runBlocking {
            val story = AiTestFixture.aiStory()
            articleAdapter.openStory(story, AiTestFixture.storyArticle(attachedAt = now))
            val accepted = story.accept(listOf(AiKeyword.of("GPU")), 2, now.plusSeconds(60), now.plusSeconds(60))

            val outcome = articleAdapter.attach(
                article = AiTestFixture.storyArticle(newsId = newsId(1), attachedAt = now.plusSeconds(60)),
                story = accepted,
                expectedVersion = story.version
            )

            assertEquals(RecordStoryArticleOutcome.RECORDED, outcome)
            val saved = storyAdapter.findByStoryId(AiTestFixture.STORY_ID)
            assertEquals(2, saved?.pendingCount)
            assertEquals(listOf(AiKeyword.of("NVIDIA"), AiKeyword.of("GPU")), saved?.keywords)
            assertEquals(
                listOf(now, now.plusSeconds(60)),
                articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).map { it.attachedAt }
            )
        }
    }

    @Nested
    @DisplayName("update()와 findSummaryDue()")
    inner class DueQuery {

        @Test
        @DisplayName("version이 기대값일 때만 갱신한다")
        fun casUpdate() = runBlocking {
            val story = AiTestFixture.aiStory()
            storyAdapter.save(story)
            val summarized = story.summarized(1, null, now)

            assertTrue(storyAdapter.update(summarized, expectedVersion = story.version))
            assertFalse(storyAdapter.update(summarized, expectedVersion = story.version))
            assertEquals(1, storyAdapter.findByStoryId(AiTestFixture.STORY_ID)?.latestVersion)
        }

        @Test
        @DisplayName("기준 시각이 threshold를 넘긴 미요약 story만 오래된 순으로 돌려준다")
        fun findDueStories() = runBlocking {
            val threshold = now.minus(Duration.ofMinutes(60))
            // 요약 없는 오래된 story
            storyAdapter.save(AiTestFixture.aiStory(attachedAt = now.minus(Duration.ofMinutes(90))))
            // 요약 없는 최근 story
            storyAdapter.save(AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID, attachedAt = now))
            // 마지막 버전이 오래된 story (미요약 있음)
            val summarizedId = StoryId.of(newsId(7))
            val summarized = AiTestFixture.aiStory(storyId = summarizedId, attachedAt = now.minus(Duration.ofHours(3)))
                .summarized(1, null, now.minus(Duration.ofHours(2)))
                .accept(listOf(AiKeyword.of("NVIDIA")), 2, now.minus(Duration.ofMinutes(70)), now)
            storyAdapter.save(summarized)
            // 흡수된 story
            storyAdapter.save(
                AiStory.trackMerged(StoryId.of(newsId(8)), AiTestFixture.STORY_ID, now.minus(Duration.ofHours(2)))
            )

            val due = storyAdapter.findSummaryDue(threshold = threshold, limit = 10)

            assertEquals(setOf(AiTestFixture.STORY_ID, summarizedId), due.map { it.storyId }.toSet())
        }
    }

    @Nested
    @DisplayName("merge()")
    inner class Merge {

        @Test
        @DisplayName("두 story의 전이와 미요약 기사 이동을 함께 쓴다")
        fun mergeMovesArticles() = runBlocking {
            val absorbed = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID)
            val absorbing = AiTestFixture.aiStory()
            articleAdapter.openStory(absorbed, AiTestFixture.storyArticle(newsId = newsId(1), storyId = AiTestFixture.OTHER_STORY_ID))
            articleAdapter.openStory(absorbing, AiTestFixture.storyArticle(newsId = newsId(2)))

            val applied = storyAdapter.merge(
                absorbed = absorbed.mergeInto(absorbing.storyId, now),
                expectedAbsorbedVersion = absorbed.version,
                absorbing = absorbing.absorb(1, absorbed.oldestPendingAt, absorbed.keywords, now),
                expectedAbsorbingVersion = absorbing.version
            )

            assertTrue(applied)
            assertTrue(storyAdapter.findByStoryId(AiTestFixture.OTHER_STORY_ID)!!.merged)
            assertEquals(2, articleAdapter.findPendingByStory(AiTestFixture.STORY_ID, 10).size)
        }

        @Test
        @DisplayName("한쪽 CAS라도 어긋나면 아무것도 바꾸지 않는다")
        fun rollbackOnAnyCasMiss() = runBlocking {
            val absorbed = AiTestFixture.aiStory(storyId = AiTestFixture.OTHER_STORY_ID)
            val absorbing = AiTestFixture.aiStory()
            articleAdapter.openStory(absorbed, AiTestFixture.storyArticle(newsId = newsId(1), storyId = AiTestFixture.OTHER_STORY_ID))
            articleAdapter.openStory(absorbing, AiTestFixture.storyArticle(newsId = newsId(2)))

            val applied = storyAdapter.merge(
                absorbed = absorbed.mergeInto(absorbing.storyId, now),
                expectedAbsorbedVersion = absorbed.version,
                absorbing = absorbing.absorb(1, absorbed.oldestPendingAt, absorbed.keywords, now),
                expectedAbsorbingVersion = absorbing.version + 5
            )

            assertFalse(applied)
            assertNull(storyAdapter.findByStoryId(AiTestFixture.OTHER_STORY_ID)?.mergedInto)
            assertEquals(1, articleAdapter.findPendingByStory(AiTestFixture.OTHER_STORY_ID, 10).size)
        }
    }
}
