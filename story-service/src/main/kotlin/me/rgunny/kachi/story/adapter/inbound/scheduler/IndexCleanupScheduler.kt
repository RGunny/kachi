package me.rgunny.kachi.story.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.story.adapter.inbound.cleanup.AlreadyRunningIndexCleanupExecution
import me.rgunny.kachi.story.adapter.inbound.cleanup.CompletedIndexCleanupExecution
import me.rgunny.kachi.story.adapter.inbound.cleanup.IndexCleanupExecutor
import me.rgunny.kachi.story.adapter.inbound.cleanup.UnavailableIndexCleanupExecution
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 색인 정리를 실행하는 scheduler.
 *
 * 중복 실행 방지와 유스케이스 호출은 [IndexCleanupExecutor]에 위임한다.
 */
@Component
class IndexCleanupScheduler(
    private val executor: IndexCleanupExecutor,
    private val settings: IndexCleanupSchedulerSettings
) {
    @PostConstruct
    fun logSchedulerSettings() {
        log.info(
            "Index cleanup scheduler configured: enabled={}, interval={}, initialDelay={}",
            settings.enabled,
            settings.interval,
            settings.initialDelay
        )
    }

    @Scheduled(
        fixedDelayString = "\${kachi.story.jobs.cleanup.interval}",
        initialDelayString = "\${kachi.story.jobs.cleanup.initial-delay}"
    )
    suspend fun cleanupCandidateIndex() {
        // 1. 자동 실행을 꺼야 하는 환경에서는 이번 tick을 건너뛴다.
        if (!settings.enabled) {
            return
        }

        // 2. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
        runCatching {
            executor.execute()
        }.onSuccess { execution ->
            when (execution) {
                is CompletedIndexCleanupExecution ->
                    log.info("Scheduled index cleanup finished: threshold={}", execution.result.threshold)

                is AlreadyRunningIndexCleanupExecution ->
                    log.info(
                        "Skip scheduled index cleanup because another cleanup is running: acquiredAt={}",
                        execution.holder.acquiredAt
                    )

                is UnavailableIndexCleanupExecution ->
                    log.warn("Skip scheduled index cleanup because the execution lock could not be checked", execution.cause)
            }
        }.onFailure { error ->
            log.warn("Scheduled index cleanup failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(IndexCleanupScheduler::class.java)
    }
}
