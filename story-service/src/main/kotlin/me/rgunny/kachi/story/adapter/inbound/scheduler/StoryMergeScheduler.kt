package me.rgunny.kachi.story.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.story.adapter.inbound.merge.AlreadyRunningStoryMergeExecution
import me.rgunny.kachi.story.adapter.inbound.merge.CompletedStoryMergeExecution
import me.rgunny.kachi.story.adapter.inbound.merge.StoryMergeExecutor
import me.rgunny.kachi.story.adapter.inbound.merge.UnavailableStoryMergeExecution
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 병합을 실행하는 scheduler.
 *
 * 중복 실행 방지와 유스케이스 호출은 [StoryMergeExecutor]에 위임한다.
 */
@Component
class StoryMergeScheduler(
    private val executor: StoryMergeExecutor,
    private val settings: StoryMergeSchedulerSettings
) {
    @PostConstruct
    fun logSchedulerSettings() {
        log.info(
            "Story merge scheduler configured: enabled={}, interval={}, initialDelay={}, scanWindow={}, scanLimit={}",
            settings.enabled,
            settings.interval,
            settings.initialDelay,
            settings.scanWindow,
            settings.scanLimit
        )
    }

    @Scheduled(
        fixedDelayString = "\${kachi.story.jobs.merge.interval}",
        initialDelayString = "\${kachi.story.jobs.merge.initial-delay}"
    )
    suspend fun mergeOpenStories() {
        // 1. 자동 실행을 꺼야 하는 환경에서는 이번 tick을 건너뛴다.
        if (!settings.enabled) {
            return
        }

        // 2. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
        runCatching {
            executor.execute()
        }.onSuccess { execution ->
            when (execution) {
                is CompletedStoryMergeExecution ->
                    log.info(
                        "Scheduled story merge finished: scanned={}, merged={}, conflicted={}, indexReassignFailures={}",
                        execution.result.scannedCount,
                        execution.result.mergedCount,
                        execution.result.conflictedCount,
                        execution.result.indexReassignFailureCount
                    )

                is AlreadyRunningStoryMergeExecution ->
                    log.info(
                        "Skip scheduled story merge because another merge is running: acquiredAt={}",
                        execution.holder.acquiredAt
                    )

                is UnavailableStoryMergeExecution ->
                    log.warn("Skip scheduled story merge because the execution lock could not be checked", execution.cause)
            }
        }.onFailure { error ->
            log.warn("Scheduled story merge failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoryMergeScheduler::class.java)
    }
}
