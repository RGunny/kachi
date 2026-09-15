package me.rgunny.kachi.story.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.story.adapter.inbound.outbox.AlreadyRunningStoryOutboxRelayExecution
import me.rgunny.kachi.story.adapter.inbound.outbox.CompletedStoryOutboxRelayExecution
import me.rgunny.kachi.story.adapter.inbound.outbox.StoryOutboxRelayExecutor
import me.rgunny.kachi.story.adapter.inbound.outbox.UnavailableStoryOutboxRelayExecution
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * outbox relay를 실행하는 scheduler.
 */
@Component
@ConditionalOnProperty(
    prefix = "kachi.story.outbox.relay",
    name = ["enabled"],
    havingValue = "true"
)
class StoryOutboxRelayScheduler(
    private val executor: StoryOutboxRelayExecutor,
    private val settings: StoryOutboxRelaySchedulerSettings
) {
    @PostConstruct
    fun logSchedulerSettings() {
        log.info(
            "Story outbox relay scheduler configured: publisherId={}, fixedDelay={}, initialDelay={}, batchSize={}, visibilityTimeout={}",
            settings.publisherId,
            settings.fixedDelay,
            settings.initialDelay,
            settings.batchSize,
            settings.publishingVisibilityTimeout
        )
    }

    @Scheduled(
        fixedDelayString = "\${kachi.story.outbox.relay.fixed-delay}",
        initialDelayString = "\${kachi.story.outbox.relay.initial-delay}"
    )
    suspend fun relay() {
        // 1. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
        runCatching {
            executor.execute()
        }.onSuccess { execution ->
            when (execution) {
                // 2. 다룰 행이 없는 tick이 대부분이므로 처리한 행이 있을 때만 집계를 남긴다.
                is CompletedStoryOutboxRelayExecution ->
                    if (execution.result.processed > 0) {
                        log.info(
                            "Scheduled outbox relay finished: processed={}, published={}, retried={}, dead={}, staleRecovered={}, completedAt={}",
                            execution.result.processed,
                            execution.result.published,
                            execution.result.retried,
                            execution.result.dead,
                            execution.result.staleRecovered,
                            execution.result.completedAt
                        )
                    }

                // 3. 이미 실행 중인 tick이 있으면 실패로 보지 않고 이번 tick만 건너뛴다.
                is AlreadyRunningStoryOutboxRelayExecution ->
                    log.info(
                        "Skip scheduled outbox relay because another relay is running: acquiredAt={}",
                        execution.holder.acquiredAt
                    )

                // 4. lock을 확인할 수 없는 것은 건너뛴 것이 아니라 장애이므로 원인과 함께 남긴다.
                is UnavailableStoryOutboxRelayExecution ->
                    log.warn("Skip scheduled outbox relay because the execution lock could not be checked", execution.cause)
            }
        }.onFailure { error ->
            log.warn("Scheduled outbox relay failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoryOutboxRelayScheduler::class.java)
    }
}
