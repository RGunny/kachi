package me.rgunny.kachi.story.fake

import java.time.Instant
import me.rgunny.kachi.story.application.port.outbound.story.StoryArticlePersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.StoryAssemblyPersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.StoryPersistencePort
import me.rgunny.kachi.story.application.port.outbound.story.model.AttachOutcome
import me.rgunny.kachi.story.domain.NewsId
import me.rgunny.kachi.story.domain.Story
import me.rgunny.kachi.story.domain.StoryArticle
import me.rgunny.kachi.story.domain.StoryId
import me.rgunny.kachi.story.domain.StoryStatus
import me.rgunny.kachi.story.domain.outbox.StoryOutbox

/**
 * story·기사·outbox를 한 메모리 저장 상태로 두고 세 영속 포트를 함께 대신하는 fake.
 *
 * 조립 쓰기의 결과는 [attachOutcomes]에 지정한 값을 먼저 쓰고, 비면 저장 상태로 판정한다.
 */
class InMemoryStoryStore : StoryPersistencePort, StoryArticlePersistencePort, StoryAssemblyPersistencePort {

    val stories: MutableMap<StoryId, Story> = linkedMapOf()
    val articles: MutableMap<NewsId, StoryArticle> = linkedMapOf()
    val outboxes: MutableList<StoryOutbox> = mutableListOf()

    val openStoryCalls: MutableList<Story> = mutableListOf()

    /** attach 호출마다 넘어온 story와 expectedVersion. */
    val attachCalls: MutableList<Pair<Story, Long>> = mutableListOf()

    /** 호출 순서별로 강제할 쓰기 결과. 비면 저장 상태로 판정한다. */
    val attachOutcomes: ArrayDeque<AttachOutcome> = ArrayDeque()

    /** 쓰기 직전에 실행할 동작. 다른 인스턴스의 동시 쓰기를 흉내 낸다. */
    var beforeWrite: (suspend () -> Unit)? = null

    /** 다음 쓰기에서 던질 예외. */
    var failure: Throwable? = null

    fun seed(story: Story, vararg storyArticles: StoryArticle) {
        stories[story.id] = story
        storyArticles.forEach { articles[it.newsId] = it }
    }

    // ---- StoryPersistencePort ----

    override suspend fun save(story: Story): Story {
        stories[story.id] = story

        return story
    }

    override suspend fun findById(id: StoryId): Story? = stories[id]

    override suspend fun findByIds(ids: Collection<StoryId>): List<Story> = ids.mapNotNull { stories[it] }

    override suspend fun update(story: Story, expectedVersion: Long): Boolean {
        val stored = stories[story.id] ?: return false
        if (stored.version != expectedVersion) {
            return false
        }
        stories[story.id] = story

        return true
    }

    override suspend fun findOpenWithLastArticleBefore(threshold: Instant, limit: Int): List<Story> {
        return stories.values
            .filter { it.status == StoryStatus.OPEN && it.lastArticleAt.isBefore(threshold) }
            .sortedBy { it.lastArticleAt }
            .take(limit)
    }

    override suspend fun find(status: StoryStatus?, openedAfter: Instant?, limit: Int): List<Story> {
        return stories.values
            .filter { status == null || it.status == status }
            .filter { openedAfter == null || !it.openedAt.isBefore(openedAfter) }
            .sortedByDescending { it.openedAt }
            .take(limit)
    }

    // ---- StoryArticlePersistencePort ----

    override suspend fun findByNewsId(newsId: NewsId): StoryArticle? = articles[newsId]

    override suspend fun findRecentByStory(storyId: StoryId, limit: Int): List<StoryArticle> {
        return articles.values
            .filter { it.storyId == storyId }
            .sortedByDescending { it.attachedAt }
            .take(limit)
    }

    override suspend fun findByStory(storyId: StoryId): List<StoryArticle> {
        return articles.values
            .filter { it.storyId == storyId }
            .sortedBy { it.attachedAt }
    }

    override suspend fun reassign(from: StoryId, to: StoryId): Long {
        val moved = articles.values.filter { it.storyId == from }
        moved.forEach { articles[it.newsId] = it.reassign(to) }

        return moved.size.toLong()
    }

    override suspend fun findCollectedAfter(threshold: Instant, after: NewsId?, limit: Int): List<StoryArticle> {
        return articles.values
            .filter { !it.collectedAt.isBefore(threshold) }
            .filter { after == null || it.newsId.value > after.value }
            .sortedBy { it.newsId.value }
            .take(limit)
    }

    // ---- StoryAssemblyPersistencePort ----

    override suspend fun openStory(story: Story, article: StoryArticle, outbox: StoryOutbox): AttachOutcome {
        openStoryCalls += story
        beforeWrite?.invoke()
        failure?.let { throw it }
        attachOutcomes.removeFirstOrNull()?.let { return it }

        if (articles.containsKey(article.newsId)) {
            return AttachOutcome.DUPLICATED
        }
        write(story, article, outbox)

        return AttachOutcome.ATTACHED
    }

    override suspend fun attach(article: StoryArticle, story: Story, expectedVersion: Long, outbox: StoryOutbox): AttachOutcome {
        attachCalls += story to expectedVersion
        beforeWrite?.invoke()
        failure?.let { throw it }
        attachOutcomes.removeFirstOrNull()?.let { return it }

        if (articles.containsKey(article.newsId)) {
            return AttachOutcome.DUPLICATED
        }
        val stored = stories[story.id]
        if (stored == null || stored.version != expectedVersion) {
            return AttachOutcome.STORY_CHANGED
        }
        write(story, article, outbox)

        return AttachOutcome.ATTACHED
    }

    private fun write(story: Story, article: StoryArticle, outbox: StoryOutbox) {
        articles[article.newsId] = article
        stories[story.id] = story
        outboxes += outbox
    }
}
