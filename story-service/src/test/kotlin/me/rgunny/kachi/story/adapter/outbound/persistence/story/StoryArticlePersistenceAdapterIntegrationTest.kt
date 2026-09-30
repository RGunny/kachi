package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.story.domain.ArticleLanguage
import me.rgunny.kachi.story.domain.ArticleSource
import me.rgunny.kachi.story.domain.AutoMergedLinkDecision
import me.rgunny.kachi.story.domain.JudgedLinkDecision
import me.rgunny.kachi.story.domain.LinkDecision
import me.rgunny.kachi.story.domain.NewStoryLinkDecision
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryJudge
import me.rgunny.kachi.story.domain.StoryKeyword
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@DisplayName("StoryArticlePersistenceAdapter 통합 테스트")
class StoryArticlePersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: StoryArticlePersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val now = StoryTestFixture.NOW
    private val storyId = StoryId.newId()
    private val otherStoryId = StoryId.newId()

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(StoryArticleMongoDocument::class.java).all().block()
    }

    @Nested
    @DisplayName("findByNewsId()")
    inner class FindByNewsId {

        @Test
        @DisplayName("기사 사본의 전 필드와 임베딩을 그대로 복원한다")
        fun restoreAllFields() = runBlocking {
            val article = insert(
                StoryTestFixture.article(
                    newsId = newsId(1),
                    title = "삼성전자 HBM 공급",
                    excerpt = "삼성전자가 HBM4 공급을 시작했다",
                    url = "https://kachi.com/news/hbm",
                    source = ArticleSource.GOOGLE,
                    language = "en-US",
                    publishedAt = now.minus(Duration.ofHours(2)),
                    collectedAt = now.minus(Duration.ofMinutes(5)),
                    keywords = listOf("samsung", "hbm"),
                    embedding = StoryTestFixture.embedding(0.1f, -2.5f, 3.75f),
                    storyId = storyId,
                    decision = NewStoryLinkDecision(candidateStoryId = otherStoryId, similarity = 0.42),
                    attachedAt = now
                )
            )

            val found = adapter.findByNewsId(article.newsId)

            assertNotNull(found)
            assertEquals(article.newsId, found.newsId)
            assertEquals("삼성전자 HBM 공급", found.title)
            assertEquals("삼성전자가 HBM4 공급을 시작했다", found.excerpt)
            assertEquals("https://kachi.com/news/hbm", found.url)
            assertEquals(ArticleSource.GOOGLE, found.source)
            assertEquals(ArticleLanguage.of("en"), found.language)
            assertEquals(now.minus(Duration.ofHours(2)), found.publishedAt)
            assertEquals(now.minus(Duration.ofMinutes(5)), found.collectedAt)
            assertEquals(listOf(StoryKeyword.of("samsung"), StoryKeyword.of("hbm")), found.matchedKeywords)
            assertEquals(article.embedding, found.embedding)
            assertEquals(storyId, found.storyId)
            assertEquals(NewStoryLinkDecision(candidateStoryId = otherStoryId, similarity = 0.42), found.decision)
            assertEquals(now, found.attachedAt)
        }

        @Test
        @DisplayName("자동 병합 판정과 judge 판정도 그대로 복원한다")
        fun restoreEachDecision() = runBlocking {
            val autoMerged = insert(article(1, decision = AutoMergedLinkDecision(storyId = storyId, similarity = 0.91)))
            val judged = insert(
                article(
                    2,
                    decision = JudgedLinkDecision(
                        candidateStoryId = storyId,
                        similarity = 0.65,
                        judge = StoryJudge.BGE_RERANKER_V2_M3,
                        judgeScore = 0.8,
                        merged = true
                    )
                )
            )
            val rejected = insert(
                article(
                    3,
                    storyId = otherStoryId,
                    decision = JudgedLinkDecision(
                        candidateStoryId = storyId,
                        similarity = 0.62,
                        judge = StoryJudge.THRESHOLD_ONLY,
                        judgeScore = 0.2,
                        merged = false
                    )
                )
            )

            assertEquals(autoMerged.decision, adapter.findByNewsId(autoMerged.newsId)?.decision)
            assertEquals(judged.decision, adapter.findByNewsId(judged.newsId)?.decision)
            assertEquals(rejected.decision, adapter.findByNewsId(rejected.newsId)?.decision)
        }

        @Test
        @DisplayName("없는 기사는 null이다")
        fun returnNullWhenAbsent() = runBlocking {
            assertNull(adapter.findByNewsId(newsId(99)))
        }
    }

    @Nested
    @DisplayName("findRecentByStory() / findByStory()")
    inner class FindByStory {

        @Test
        @DisplayName("그 story의 기사만 최근에 붙은 순으로 limit건 읽는다")
        fun findRecentInOrder() = runBlocking {
            val oldest = insert(article(1, attachedAt = now.minus(Duration.ofHours(2))))
            val middle = insert(article(2, attachedAt = now.minus(Duration.ofHours(1))))
            val latest = insert(article(3, attachedAt = now))
            insert(article(4, storyId = otherStoryId, attachedAt = now))

            assertEquals(listOf(latest.newsId, middle.newsId), adapter.findRecentByStory(storyId, limit = 2).map { it.newsId })
            assertEquals(
                listOf(oldest.newsId, middle.newsId, latest.newsId),
                adapter.findByStory(storyId).map { it.newsId }
            )
        }

        @Test
        @DisplayName("기사가 없는 story는 빈 목록이다")
        fun returnEmptyWhenNoArticles() = runBlocking {
            assertTrue(adapter.findRecentByStory(storyId, limit = 5).isEmpty())
            assertTrue(adapter.findByStory(storyId).isEmpty())
        }
    }

    @Nested
    @DisplayName("reassign()")
    inner class Reassign {

        @Test
        @DisplayName("from의 기사를 전부 to로 옮기고 옮긴 수를 돌려준다")
        fun moveAllArticles() = runBlocking {
            insert(article(1))
            insert(article(2))
            val untouched = insert(article(3, storyId = otherStoryId))

            val moved = adapter.reassign(from = storyId, to = otherStoryId)

            assertEquals(2, moved)
            assertTrue(adapter.findByStory(storyId).isEmpty())
            assertEquals(3, adapter.findByStory(otherStoryId).size)
            assertEquals(otherStoryId, adapter.findByNewsId(untouched.newsId)?.storyId)
        }

        @Test
        @DisplayName("옮길 기사가 없으면 0이다")
        fun returnZeroWhenNothingToMove() = runBlocking {
            assertEquals(0, adapter.reassign(from = storyId, to = otherStoryId))
        }
    }

    @Nested
    @DisplayName("findCollectedAfter()")
    inner class FindCollectedAfter {

        @Test
        @DisplayName("기준 이후 수집된 기사를 기사 id 순으로 페이지 읽는다")
        fun pageByNewsId() = runBlocking {
            insert(article(1, collectedAt = now.minus(Duration.ofHours(5))))
            val first = insert(article(2, collectedAt = now.minus(Duration.ofHours(1))))
            val second = insert(article(3, collectedAt = now))
            val third = insert(article(4, collectedAt = now.plus(Duration.ofHours(1))))
            val threshold = now.minus(Duration.ofHours(2))

            val firstPage = adapter.findCollectedAfter(threshold, after = null, limit = 2)
            val secondPage = adapter.findCollectedAfter(threshold, after = firstPage.last().newsId, limit = 2)

            assertEquals(listOf(first.newsId, second.newsId), firstPage.map { it.newsId })
            assertEquals(listOf(third.newsId), secondPage.map { it.newsId })
        }

        @Test
        @DisplayName("기준 시각과 같은 collectedAt은 포함한다")
        fun includeThreshold() = runBlocking {
            val onThreshold = insert(article(1, collectedAt = now))

            assertEquals(listOf(onThreshold.newsId), adapter.findCollectedAfter(now, after = null, limit = 10).map { it.newsId })
        }
    }

    @Nested
    @DisplayName("index")
    inner class Indexes {

        @Test
        @DisplayName("collection에 선언한 index가 만들어진다")
        fun createDeclaredIndexes() {
            val indexNames = mongoTemplate.indexOps(StoryArticleMongoDocument::class.java)
                .indexInfo
                .collectList()
                .block()
                .orEmpty()
                .map { it.name }

            assertTrue(
                indexNames.containsAll(listOf("ix_story_articles_story_id_attached_at", "ix_story_articles_collected_at")),
                "생성된 index: $indexNames"
            )
        }
    }

    private suspend fun insert(article: StoryArticle): StoryArticle {
        mongoTemplate.insert(StoryArticleMongoDocument.fromDomain(article)).awaitSingle()

        return article
    }

    /** 번호 순서가 곧 id 순서인 기사 id. */
    private fun newsId(sequence: Int): NewsId {
        return NewsId.of(UUID.fromString("018f0000-0000-7000-8000-%012x".format(sequence)))
    }

    private fun article(
        sequence: Int,
        storyId: StoryId = this.storyId,
        decision: LinkDecision = NewStoryLinkDecision(candidateStoryId = null, similarity = null),
        collectedAt: Instant = now,
        attachedAt: Instant = now
    ): StoryArticle {
        return StoryTestFixture.article(
            newsId = newsId(sequence),
            url = "https://kachi.com/news/$sequence",
            storyId = storyId,
            decision = decision,
            collectedAt = collectedAt,
            attachedAt = attachedAt
        )
    }
}
