package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.story.application.port.outbound.story.model.AttachOutcome
import me.rgunny.kachi.story.domain.AutoMergedLinkDecision
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.support.StoryOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@DisplayName("StoryAssemblyPersistenceAdapter 통합 테스트")
class StoryAssemblyPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: StoryAssemblyPersistenceAdapter

    @Autowired
    private lateinit var storyAdapter: StoryPersistenceAdapter

    @Autowired
    private lateinit var articleAdapter: StoryArticlePersistenceAdapter

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    private val now = StoryTestFixture.NOW
    private val outboxCollection by lazy { StoryOutboxCollection(mongoTemplate) }

    @BeforeEach
    fun cleanUp() {
        mongoTemplate.remove(StoryMongoDocument::class.java).all().block()
        mongoTemplate.remove(StoryArticleMongoDocument::class.java).all().block()
        outboxCollection.clear()
    }

    @Nested
    @DisplayName("openStory()")
    inner class OpenStory {

        @Test
        @DisplayName("기사·story·outbox를 함께 저장한다")
        fun saveAllThree() = runBlocking {
            val first = firstArticle()
            val story = Story.open(first, now)

            val outcome = adapter.openStory(story, first, outboxFor(story, first))

            assertEquals(AttachOutcome.ATTACHED, outcome)
            assertEquals(0, storyAdapter.findById(story.id)?.version)
            assertEquals(story.id, articleAdapter.findByNewsId(first.newsId)?.storyId)
            assertEquals(listOf(eventKey(story, first)), outboxCollection.findAll().map { it.eventKey })
        }

        @Test
        @DisplayName("같은 기사가 이미 있으면 DUPLICATED이고 story와 outbox도 남기지 않는다")
        fun rejectDuplicatedArticle() = runBlocking {
            val first = firstArticle()
            adapter.openStory(Story.open(first, now), first, outboxFor(Story.open(first, now), first))
            val again = StoryTestFixture.article(newsId = first.newsId, storyId = StoryId.newId())
            val anotherStory = Story.open(again, now)

            val outcome = adapter.openStory(anotherStory, again, outboxFor(anotherStory, again))

            assertEquals(AttachOutcome.DUPLICATED, outcome)
            assertNull(storyAdapter.findById(anotherStory.id))
            assertEquals(1, outboxCollection.findAll().size)
        }
    }

    @Nested
    @DisplayName("attach()")
    inner class Attach {

        @Test
        @DisplayName("version이 맞으면 기사·story 전이·outbox를 함께 쓴다")
        fun attachWithMatchingVersion() = runBlocking {
            val story = openedStory()
            val second = secondArticle(story)
            val attached = story.attach(second, now.plusSeconds(5))

            val outcome = adapter.attach(second, attached, expectedVersion = 0, outbox = outboxFor(attached, second))

            assertEquals(AttachOutcome.ATTACHED, outcome)
            val found = assertNotNull(storyAdapter.findById(story.id))
            assertEquals(1, found.version)
            assertEquals(2, found.articleCount)
            assertEquals(story.id, articleAdapter.findByNewsId(second.newsId)?.storyId)
            assertEquals(2, outboxCollection.findAll().size)
        }

        @Test
        @DisplayName("version이 어긋나면 STORY_CHANGED이고 기사와 outbox도 되돌린다")
        fun rollbackWhenStoryChanged() = runBlocking {
            val story = openedStory()
            val second = secondArticle(story)
            val attached = story.attach(second, now.plusSeconds(5))

            val outcome = adapter.attach(second, attached, expectedVersion = 7, outbox = outboxFor(attached, second))

            assertEquals(AttachOutcome.STORY_CHANGED, outcome)
            assertEquals(0, storyAdapter.findById(story.id)?.version)
            assertNull(articleAdapter.findByNewsId(second.newsId))
            assertEquals(1, outboxCollection.findAll().size)
        }

        @Test
        @DisplayName("같은 기사가 이미 있으면 DUPLICATED이고 story를 바꾸지 않는다")
        fun rejectDuplicatedArticle() = runBlocking {
            val story = openedStory()
            val first = articleAdapter.findByStory(story.id).single()
            val attached = story.attach(first, now.plusSeconds(5))

            val outcome = adapter.attach(first, attached, expectedVersion = 0, outbox = outboxFor(attached, first))

            assertEquals(AttachOutcome.DUPLICATED, outcome)
            assertEquals(0, storyAdapter.findById(story.id)?.version)
            assertEquals(1, outboxCollection.findAll().size)
        }
    }

    private suspend fun openedStory(): Story {
        val first = firstArticle()
        val story = Story.open(first, now)
        adapter.openStory(story, first, outboxFor(story, first))

        return story
    }

    private fun firstArticle(): StoryArticle {
        return StoryTestFixture.article(newsId = NewsId.of(UUID.randomUUID()), storyId = StoryId.newId())
    }

    private fun secondArticle(story: Story): StoryArticle {
        return StoryTestFixture.article(
            newsId = NewsId.of(UUID.randomUUID()),
            url = "https://kachi.com/news/2",
            storyId = story.id,
            decision = AutoMergedLinkDecision(storyId = story.id, similarity = 0.9),
            embedding = StoryTestFixture.embedding(0f, 1f)
        )
    }

    private fun outboxFor(story: Story, article: StoryArticle): StoryOutbox {
        return StoryTestFixture.outbox(
            eventType = StoryOutboxEventType.ARTICLE_ATTACHED,
            eventKey = eventKey(story, article),
            partitionKey = story.id.value.toString()
        )
    }

    private fun eventKey(story: Story, article: StoryArticle): String {
        return "${story.id.value}:${article.newsId.value}"
    }
}
