package me.rgunny.kachi.story.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.story.adapter.inbound.close.AlreadyRunningStoryCloseExecution
import me.rgunny.kachi.story.adapter.inbound.close.CompletedStoryCloseExecution
import me.rgunny.kachi.story.adapter.inbound.close.StoryCloseExecutor
import me.rgunny.kachi.story.adapter.inbound.close.UnavailableStoryCloseExecution
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 닫기를 실행하는 scheduler.
 *
 * 중복 실행 방지와 유스케이스 호출은 [StoryCloseExecutor]에 위임한다.
 */
@Component
class StoryCloseScheduler(
    private val executor: StoryCloseExecutor,
    private val settings: StoryCloseSchedulerSettings
) {
    @PostConstruct
    fun logSchedulerSettings() {
        log.info(
            "Story close scheduler configured: enabled={}, interval={}, initialDelay={}, closeAfter={}, batchLimit={}",
            settings.enabled,
            settings.interval,
            settings.initialDelay,
            settings.closeAfter,
            settings.batchLimit
        )
    }

    @Scheduled(
        fixedDelayString = "\${kachi.story.jobs.close.interval}",
        initialDelayString = "\${kachi.story.jobs.close.initial-delay}"
    )
    suspend fun closeIdleStories() {
        // 1. 자동 실행을 꺼야 하는 환경에서는 이번 tick을 건너뛴다.
        if (!settings.enabled) {
            return
        }

        // 2. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
        runCatching {
            executor.execute()
        }.onSuccess { execution ->
            when (execution) {
                is CompletedStoryCloseExecution ->
                    log.info(
                        "Scheduled story close finished: threshold={}, closed={}, conflicted={}, indexDeleteFailures={}",
                        execution.result.threshold,
                        execution.result.closedCount,
                        execution.result.conflictedCount,
                        execution.result.indexDeleteFailureCount
                    )

                is AlreadyRunningStoryCloseExecution ->
                    log.info(
                        "Skip scheduled story close because another close is running: acquiredAt={}",
                        execution.holder.acquiredAt
                    )

                is UnavailableStoryCloseExecution ->
                    log.warn("Skip scheduled story close because the execution lock could not be checked", execution.cause)
            }
        }.onFailure { error ->
            log.warn("Scheduled story close failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(StoryCloseScheduler::class.java)
    }
}
