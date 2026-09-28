package me.rgunny.kachi.ai.application.service.story

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeDueStoriesUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeStoryUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.CreatedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.FailedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SkippedStorySummaryResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeDueStoriesResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryPersistencePort
import me.rgunny.kachi.ai.domain.story.AiStory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * maxWait를 넘긴 story들을 찾아 순서대로 요약하는 유스케이스.
 *
 * 예외는 story 단위로 가두고, 호출할 수 있는 LLM 모델이 하나도 없으면 남은 story는 호출하지 않고 끝낸다.
 */
@Service
class SummarizeDueStoriesService(
    private val aiStoryPersistencePort: AiStoryPersistencePort,
    private val summarizeStoryUseCase: SummarizeStoryUseCase,
    private val policy: StorySummaryPolicy,
    private val clock: Clock
) : SummarizeDueStoriesUseCase {

    override suspend fun summarizeDue(command: SummarizeDueStoriesCommand): SummarizeDueStoriesResult {
        val now = Instant.now(clock)
        val due = aiStoryPersistencePort.findSummaryDue(
            threshold = policy.dueThreshold(now),
            limit = command.maxStories
        )

        var created = 0
        var skipped = 0
        var failed = 0
        var aborted = false

        for (story in due) {
            if (aborted) {
                skipped += 1
                continue
            }

            when (summarizeDueStory(story, onAbort = { aborted = true })) {
                is CreatedStorySummaryResult -> created += 1
                is SkippedStorySummaryResult -> skipped += 1
                is FailedStorySummaryResult -> failed += 1
                null -> failed += 1
            }
        }

        return SummarizeDueStoriesResult(
            due = due.size,
            created = created,
            skipped = skipped,
            failed = failed,
            aborted = aborted
        )
    }

    /**
     * story 하나를 요약하고 예외를 여기서 가둔다.
     *
     * 예외로 끝난 실행은 null을 돌려준다.
     */
    private suspend fun summarizeDueStory(
        story: AiStory,
        onAbort: () -> Unit
    ): SummarizeStoryResult? {
        return try {
            summarizeStoryUseCase.summarize(SummarizeStoryCommand(storyId = story.storyId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = (error as? LlmProviderException)?.failure
            // 실제 호출 없이 끝난 실패(후보 전부 차단)
            if (failure != null && !failure.fromActualCall) {
                log.warn(
                    "Aborting remaining due stories because no LLM model can be called: storyId={}",
                    story.storyId.value
                )
                onAbort()
            } else {
                log.warn("Due story summary failed and waits for the next tick: storyId={}", story.storyId.value, error)
            }

            null
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(SummarizeDueStoriesService::class.java)
    }
}
