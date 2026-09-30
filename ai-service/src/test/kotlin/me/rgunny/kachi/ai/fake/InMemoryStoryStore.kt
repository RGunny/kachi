package me.rgunny.kachi.ai.fake

import java.time.Instant
import java.util.UUID
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryArticlePersistencePort
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryPersistencePort
import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.application.port.outbound.summary.StorySummaryPersistencePort
import me.rgunny.kachi.ai.domain.outbox.AiOutbox
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.AiStoryArticle
import me.rgunny.kachi.ai.domain.story.StoryId
import me.rgunny.kachi.ai.domain.summary.StorySummary

/**
 * story 상태·기사 사본·요약 버전 포트 셋을 한 저장 상태로 구현하는 fake.
 *
 * [attachOutcomeOverride]·[saveVersionResult]·[mergeResult]가 있으면 저장 상태와 무관하게 그 값을 돌려준다.
 */
class InMemoryStoryStore :
    AiStoryPersistencePort,
    AiStoryArticlePersistencePort,
    StorySummaryPersistencePort {

    val stories = mutableMapOf<StoryId, AiStory>()
    val articles = mutableMapOf<UUID, AiStoryArticle>()
    val summaries = mutableListOf<StorySummary>()
    val outboxes = mutableListOf<AiOutbox>()
    var attachOutcomeOverride: RecordStoryArticleOutcome? = null
    var saveVersionResult: Boolean? = null
    var mergeResult: Boolean? = null

    override suspend fun findByStoryId(storyId: StoryId): AiStory? = stories[storyId]

    override suspend fun save(story: AiStory): AiStory {
        check(story.storyId !in stories) { "story가 이미 있습니다: ${story.storyId.value}" }
        stories[story.storyId] = story

        return story
    }

    override suspend fun update(story: AiStory, expectedVersion: Long): Boolean {
        if (stories[story.storyId]?.version != expectedVersion) {
            return false
        }
        stories[story.storyId] = story

        return true
    }

    override suspend fun findSummaryDue(threshold: Instant, limit: Int): List<AiStory> {
        return stories.values
            .filter { !it.merged && it.pendingCount >= 1 }
            .filter { story -> story.summaryWaitBaseline?.let { !it.isAfter(threshold) } == true }
            .sortedBy { it.oldestPendingAt }
            .take(limit)
    }

    override suspend fun merge(
        absorbed: AiStory,
        expectedAbsorbedVersion: Long,
        absorbing: AiStory,
        expectedAbsorbingVersion: Long
    ): Boolean {
        mergeResult?.let { return it }
        if (stories[absorbed.storyId]?.version != expectedAbsorbedVersion) {
            return false
        }
        if (stories[absorbing.storyId]?.version != expectedAbsorbingVersion) {
            return false
        }

        stories[absorbed.storyId] = absorbed
        stories[absorbing.storyId] = absorbing
        articles.replaceAll { _, article ->
            if (article.storyId == absorbed.storyId && article.pending) article.reassign(absorbing.storyId) else article
        }

        return true
    }

    override suspend fun openStory(story: AiStory, article: AiStoryArticle): RecordStoryArticleOutcome {
        if (article.newsId in articles) {
            return RecordStoryArticleOutcome.DUPLICATED
        }
        if (story.storyId in stories) {
            return RecordStoryArticleOutcome.STORY_CHANGED
        }
        stories[story.storyId] = story
        articles[article.newsId] = article

        return RecordStoryArticleOutcome.RECORDED
    }

    override suspend fun attach(
        article: AiStoryArticle,
        story: AiStory,
        expectedVersion: Long
    ): RecordStoryArticleOutcome {
        attachOutcomeOverride?.let { return it }
        if (article.newsId in articles) {
            return RecordStoryArticleOutcome.DUPLICATED
        }
        if (stories[story.storyId]?.version != expectedVersion) {
            return RecordStoryArticleOutcome.STORY_CHANGED
        }
        stories[story.storyId] = story
        articles[article.newsId] = article

        return RecordStoryArticleOutcome.RECORDED
    }

    override suspend fun findPendingByStory(storyId: StoryId, limit: Int): List<AiStoryArticle> {
        return articles.values
            .filter { it.storyId == storyId && it.pending }
            .sortedBy { it.attachedAt }
            .take(limit)
    }

    override suspend fun findLatest(storyId: StoryId): StorySummary? {
        return findByStory(storyId, limit = 1).firstOrNull()
    }

    override suspend fun findByStory(storyId: StoryId, limit: Int): List<StorySummary> {
        return summaries
            .filter { it.storyId == storyId }
            .sortedByDescending { it.version }
            .take(limit)
    }

    override suspend fun saveVersion(
        summary: StorySummary,
        story: AiStory,
        expectedStoryVersion: Long,
        outboxes: List<AiOutbox>
    ): Boolean {
        saveVersionResult?.let { return it }
        if (summaries.any { it.storyId == summary.storyId && it.version == summary.version }) {
            return false
        }
        if (stories[story.storyId]?.version != expectedStoryVersion) {
            return false
        }
        val pendingTargets = summary.newNewsIds.filter { articles[it]?.pending == true }
        if (pendingTargets.size != summary.newNewsIds.size) {
            return false
        }

        pendingTargets.forEach { newsId ->
            articles[newsId] = articles.getValue(newsId).summarizedIn(summary.version)
        }
        stories[story.storyId] = story
        summaries += summary
        this.outboxes += outboxes

        return true
    }
}
