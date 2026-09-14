package me.rgunny.kachi.story.adapter.outbound.persistence.story

import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.story.adapter.outbound.persistence.PersistenceAdapterIntegrationTest
import me.rgunny.kachi.story.application.port.outbound.story.model.ReorganizeOutcome
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import me.rgunny.kachi.story.domain.outbox.StoryOutbox
import me.rgunny.kachi.story.domain.outbox.StoryOutboxEventType
import me.rgunny.kachi.story.fixture.StoryTestFixture
import me.rgunny.kachi.story.support.StoryOutboxCollection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate

@DisplayName("StoryReorganizePersistenceAdapter 통합 테스트")
class StoryReorganizePersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: StoryReorganizePersistenceAdapter

    @Autowired
    private lateinit var assemblyAdapter: StoryAssemblyPersistenceAdapter

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

    @Test
    @DisplayName("두 story 전이·기사 이전·outbox를 함께 쓴다")
    fun mergeWritesAllTogether() = runBlocking {
        val target = openedStory()
        val source = openedStory()
        val absorbed = target.absorb(source, now)
        val merged = source.mergeInto(target, now)

        val outcome = adapter.merge(absorbed, merged, expectedTargetVersion = 0, expectedSourceVersion = 0, outbox = outboxFor(merged))

        assertEquals(ReorganizeOutcome.REORGANIZED, outcome)
        val storedTarget = assertNotNull(storyAdapter.findById(target.id))
        assertEquals(1, storedTarget.version)
        assertEquals(2, storedTarget.articleCount)
        val storedSource = assertNotNull(storyAdapter.findById(source.id))
        assertEquals(StoryStatus.CLOSED, storedSource.status)
        assertEquals(target.id, storedSource.mergedInto)
        assertEquals(2, articleAdapter.findByStory(target.id).size)
        assertEquals(0, articleAdapter.findByStory(source.id).size)
        assertEquals(listOf(mergedEventKey(merged)), outboxCollection.findAll().map { it.eventKey })
    }

    @Test
    @DisplayName("target version이 어긋나면 STORY_CHANGED이고 아무것도 남지 않는다")
    fun rejectWhenTargetChanged() = runBlocking {
        val target = openedStory()
        val source = openedStory()
        val merged = source.mergeInto(target, now)

        val outcome = adapter.merge(target.absorb(source, now), merged, expectedTargetVersion = 7, expectedSourceVersion = 0, outbox = outboxFor(merged))

        assertEquals(ReorganizeOutcome.STORY_CHANGED, outcome)
        assertUnchanged(target, source)
    }

    @Test
    @DisplayName("source version이 어긋나면 앞선 target 전이도 되돌린다")
    fun rollbackTargetWhenSourceChanged() = runBlocking {
        val target = openedStory()
        val source = openedStory()
        val merged = source.mergeInto(target, now)

        val outcome = adapter.merge(target.absorb(source, now), merged, expectedTargetVersion = 0, expectedSourceVersion = 7, outbox = outboxFor(merged))

        assertEquals(ReorganizeOutcome.STORY_CHANGED, outcome)
        assertUnchanged(target, source)
    }

    @Test
    @DisplayName("같은 병합의 outbox 행이 이미 있으면 전이와 기사 이전도 되돌린다")
    fun rollbackWhenOutboxDuplicated() = runBlocking {
        val target = openedStory()
        val source = openedStory()
        val merged = source.mergeInto(target, now)
        outboxCollection.insert(outboxFor(merged))

        val outcome = adapter.merge(target.absorb(source, now), merged, expectedTargetVersion = 0, expectedSourceVersion = 0, outbox = outboxFor(merged))

        assertEquals(ReorganizeOutcome.STORY_CHANGED, outcome)
        assertUnchanged(target, source)
    }

    /** 두 story와 기사 소속이 병합 전 그대로인지 본다. */
    private suspend fun assertUnchanged(target: Story, source: Story) {
        assertEquals(0, storyAdapter.findById(target.id)?.version)
        assertEquals(StoryStatus.OPEN, storyAdapter.findById(source.id)?.status)
        assertNull(storyAdapter.findById(source.id)?.mergedInto)
        assertEquals(1, articleAdapter.findByStory(target.id).size)
        assertEquals(1, articleAdapter.findByStory(source.id).size)
    }

    private suspend fun openedStory(): Story {
        val first: StoryArticle = StoryTestFixture.article(newsId = NewsId.of(UUID.randomUUID()), storyId = StoryId.newId())
        val story = Story.open(first, now)
        val outbox = StoryTestFixture.outbox(
            eventKey = "${story.id.value}:${first.newsId.value}",
            partitionKey = story.id.value.toString()
        )
        assemblyAdapter.openStory(story, first, outbox)
        outboxCollection.clear()

        return story
    }

    private fun outboxFor(merged: Story): StoryOutbox {
        return StoryTestFixture.outbox(
            eventType = StoryOutboxEventType.MERGED,
            eventKey = mergedEventKey(merged),
            partitionKey = merged.mergedInto!!.value.toString()
        )
    }

    private fun mergedEventKey(merged: Story): String {
        return "${merged.id.value}>${merged.mergedInto!!.value}"
    }
}
