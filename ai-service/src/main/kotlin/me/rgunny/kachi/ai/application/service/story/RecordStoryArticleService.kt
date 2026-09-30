package me.rgunny.kachi.ai.application.service.story

import java.time.Instant
import java.time.Clock
import me.rgunny.kachi.ai.application.exception.StoryRecordConflictException
import me.rgunny.kachi.ai.application.port.inbound.story.RecordStoryArticleUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeStoryUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.RecordStoryArticleResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryArticlePersistencePort
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryPersistencePort
import me.rgunny.kachi.ai.application.port.outbound.story.model.RecordStoryArticleOutcome
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.AiStoryArticle
import me.rgunny.kachi.ai.domain.story.StoryId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * story에 붙은 기사를 사본으로 기록하고, 즉시 트리거를 충족하면 그 자리에서 요약까지 실행하는 유스케이스.
 *
 * 흡수된 story로 온 늦은 기사는 흡수한 story로 귀속을 옮겨 기록한다.
 * 같은 기사의 재전달은 newsId unique가 걸러내고, 그때도 트리거는 다시 평가한다(요약 직전에 끊긴 앞선 기록의 이어받기).
 */
@Service
class RecordStoryArticleService(
    private val aiStoryPersistencePort: AiStoryPersistencePort,
    private val aiStoryArticlePersistencePort: AiStoryArticlePersistencePort,
    private val summarizeStoryUseCase: SummarizeStoryUseCase,
    private val policy: StorySummaryPolicy,
    private val clock: Clock
) : RecordStoryArticleUseCase {

    override suspend fun record(command: RecordStoryArticleCommand): RecordStoryArticleResult {
        repeat(MAX_CAS_ATTEMPTS) {
            val now = Instant.now(clock)
            val story = resolveTarget(command.storyId)
            val targetStoryId = story?.storyId ?: command.storyId

            val replayed = when (recordArticle(command, story, targetStoryId, now)) {
                RecordStoryArticleOutcome.RECORDED -> false
                RecordStoryArticleOutcome.DUPLICATED -> true
                RecordStoryArticleOutcome.STORY_CHANGED -> return@repeat
            }

            return RecordStoryArticleResult(
                storyId = targetStoryId,
                replayed = replayed,
                summary = summarizeIfTriggered(targetStoryId)
            )
        }

        throw StoryRecordConflictException(storyId = command.storyId, attempts = MAX_CAS_ATTEMPTS)
    }

    /**
     * 흡수 체인을 따라가 기사가 실제로 귀속될 story를 찾는다.
     */
    private suspend fun resolveTarget(storyId: StoryId): AiStory? {
        var current = aiStoryPersistencePort.findByStoryId(storyId) ?: return null
        val visited = mutableSetOf(storyId)

        while (true) {
            val next = current.mergedInto ?: return current
            check(visited.add(next)) { "story 흡수 체인에 순환이 있습니다: ${next.value}" }
            current = aiStoryPersistencePort.findByStoryId(next)
                ?: return null
        }
    }

    private suspend fun recordArticle(
        command: RecordStoryArticleCommand,
        story: AiStory?,
        targetStoryId: StoryId,
        now: Instant
    ): RecordStoryArticleOutcome {
        val article = article(command, targetStoryId)

        return if (story == null) {
            aiStoryArticlePersistencePort.openStory(
                story = AiStory.open(
                    storyId = targetStoryId,
                    keywords = command.storyKeywords,
                    articleCount = command.storyArticleCount,
                    attachedAt = command.attachedAt,
                    now = now
                ),
                article = article
            )
        } else {
            aiStoryArticlePersistencePort.attach(
                article = article,
                story = story.accept(
                    keywords = command.storyKeywords,
                    articleCount = command.storyArticleCount,
                    attachedAt = command.attachedAt,
                    now = now
                ),
                expectedVersion = story.version
            )
        }
    }

    /**
     * 기록 후 상태를 다시 읽어 즉시 트리거를 충족하면 그 자리에서 요약한다.
     *
     * 트리거를 충족하지 않으면 null을 돌려준다.
     */
    private suspend fun summarizeIfTriggered(storyId: StoryId): SummarizeStoryResult? {
        val story = aiStoryPersistencePort.findByStoryId(storyId) ?: return null
        if (!policy.requiresImmediateSummary(story)) {
            return null
        }

        log.info(
            "Story summary triggered by pending articles: storyId={}, pending={}",
            story.storyId.value,
            story.pendingCount
        )

        return summarizeStoryUseCase.summarize(SummarizeStoryCommand(storyId = story.storyId))
    }

    private fun article(
        command: RecordStoryArticleCommand,
        targetStoryId: StoryId
    ): AiStoryArticle {
        return AiStoryArticle.create(
            newsId = command.newsId,
            storyId = targetStoryId,
            source = command.source,
            title = command.title,
            excerpt = command.excerpt,
            url = command.url,
            publishedAt = command.publishedAt,
            attachedAt = command.attachedAt
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(RecordStoryArticleService::class.java)

        /** CAS 경합 재시도 상한. */
        const val MAX_CAS_ATTEMPTS = 3
    }
}
