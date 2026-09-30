package me.rgunny.kachi.ai.application.service.story

import java.time.Clock
import java.time.Instant
import me.rgunny.kachi.ai.application.exception.StoryRecordConflictException
import me.rgunny.kachi.ai.application.port.inbound.story.ApplyStoryMergeUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.SummarizeStoryUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.ApplyStoryMergeResult
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryCommand
import me.rgunny.kachi.ai.application.port.inbound.story.model.SummarizeStoryResult
import me.rgunny.kachi.ai.application.port.outbound.story.AiStoryPersistencePort
import me.rgunny.kachi.ai.domain.story.AiStory
import me.rgunny.kachi.ai.domain.story.StoryId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * story 병합 이벤트를 받아 흡수된 story의 미요약 기사와 키워드를 흡수한 story로 옮기는 유스케이스.
 *
 * 흡수된 story의 상태가 없으면 자리표시만 남긴다.
 * 옮긴 뒤 미요약 수가 즉시 트리거를 충족하면 그 자리에서 요약한다.
 */
@Service
class ApplyStoryMergeService(
    private val aiStoryPersistencePort: AiStoryPersistencePort,
    private val summarizeStoryUseCase: SummarizeStoryUseCase,
    private val policy: StorySummaryPolicy,
    private val clock: Clock
) : ApplyStoryMergeUseCase {

    override suspend fun apply(command: ApplyStoryMergeCommand): ApplyStoryMergeResult {
        repeat(MAX_CAS_ATTEMPTS) {
            val now = Instant.now(clock)

            // 1. 흡수된 story를 모른다면 늦은 기사에 대비해 자리표시만 남긴다.
            val absorbed = aiStoryPersistencePort.findByStoryId(command.mergedStoryId)
            if (absorbed == null) {
                aiStoryPersistencePort.save(
                    AiStory.trackMerged(storyId = command.mergedStoryId, mergedInto = command.storyId, now = now)
                )
                log.info(
                    "Story merge tracked without local state: mergedStoryId={}, storyId={}",
                    command.mergedStoryId.value,
                    command.storyId.value
                )
                return ApplyStoryMergeResult(replayed = false, movedPendingCount = 0, summary = null)
            }
            if (absorbed.merged) {
                return ApplyStoryMergeResult(replayed = true, movedPendingCount = 0, summary = null)
            }

            // 2. 흡수한 story의 흡수 체인을 따라간 끝이 실제 목적지다.
            val absorbing = resolveAbsorbing(command.storyId, command.mergedStoryId)

            // 3. 두 story의 전이와 기사 이동을 한 트랜잭션으로 쓴다. 경합에서 지면 다시 읽는다.
            val moved = absorbed.pendingCount
            val applied = aiStoryPersistencePort.merge(
                absorbed = absorbed.mergeInto(target = absorbing.storyId, now = now),
                expectedAbsorbedVersion = absorbed.version,
                absorbing = absorbing.absorb(
                    movedPendingCount = moved,
                    movedOldestPendingAt = absorbed.oldestPendingAt,
                    mergedKeywords = absorbed.keywords,
                    now = now
                ),
                expectedAbsorbingVersion = absorbing.version
            )
            if (!applied) {
                return@repeat
            }

            log.info(
                "Story merge applied: mergedStoryId={}, storyId={}, movedPending={}",
                command.mergedStoryId.value,
                absorbing.storyId.value,
                moved
            )

            return ApplyStoryMergeResult(
                replayed = false,
                movedPendingCount = moved,
                summary = summarizeIfTriggered(absorbing.storyId)
            )
        }

        throw StoryRecordConflictException(storyId = command.storyId, attempts = MAX_CAS_ATTEMPTS)
    }

    /**
     * 흡수한 story의 흡수 체인을 끝까지 따라가 목적지 story를 찾는다.
     *
     * 체인의 어느 story든 상태가 없으면 [IllegalStateException]을 던진다(같은 파티션의 소비 순서 깨짐).
     */
    private suspend fun resolveAbsorbing(storyId: StoryId, mergedStoryId: StoryId): AiStory {
        var current = checkNotNull(aiStoryPersistencePort.findByStoryId(storyId)) {
            "흡수한 story의 상태가 없습니다: ${storyId.value}"
        }
        val visited = mutableSetOf(mergedStoryId, storyId)

        while (true) {
            val next = current.mergedInto ?: return current
            check(visited.add(next)) { "story 흡수 체인에 순환이 있습니다: ${next.value}" }
            current = checkNotNull(aiStoryPersistencePort.findByStoryId(next)) {
                "흡수 체인의 story 상태가 없습니다: ${next.value}"
            }
        }
    }

    private suspend fun summarizeIfTriggered(storyId: StoryId): SummarizeStoryResult? {
        val story = aiStoryPersistencePort.findByStoryId(storyId) ?: return null
        if (!policy.requiresImmediateSummary(story)) {
            return null
        }

        log.info(
            "Story summary triggered by merged pending articles: storyId={}, pending={}",
            story.storyId.value,
            story.pendingCount
        )

        return summarizeStoryUseCase.summarize(SummarizeStoryCommand(storyId = story.storyId))
    }

    private companion object {
        val log = LoggerFactory.getLogger(ApplyStoryMergeService::class.java)

        /** CAS 경합 재시도 상한. */
        const val MAX_CAS_ATTEMPTS = 3
    }
}
