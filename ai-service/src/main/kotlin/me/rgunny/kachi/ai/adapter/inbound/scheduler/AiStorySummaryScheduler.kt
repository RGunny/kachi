package me.rgunny.kachi.ai.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryExecutor
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryLockUnavailable
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryStarted
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 maxWait를 넘긴 story 요약을 실행하는 scheduler.
 *
 * 중복 실행 방지와 유스케이스 호출은 [AiStorySummaryExecutor]에 위임한다.
 */
@Component
class AiStorySummaryScheduler(
    private val executor: AiStorySummaryExecutor,
    private val properties: AiStorySummarySchedulerProperties
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "AI story summary scheduler configured: enabled={}, initialDelay={}, fixedDelay={}, maxStoriesPerTick={}",
            properties.enabled,
            properties.initialDelay,
            properties.fixedDelay,
            properties.maxStoriesPerTick
        )
    }

    @Scheduled(
        fixedDelayString = AiStorySummarySchedulerProperties.FIXED_DELAY_EXPRESSION,
        initialDelayString = AiStorySummarySchedulerProperties.INITIAL_DELAY_EXPRESSION
    )
    suspend fun summarizeDueStories() {
        // 1. 비활성 설정이면 tick을 건너뛴다.
        if (!properties.enabled) {
            return
        }

        runCatching {
            executor.execute(properties.toCommand())
        }.onSuccess { result ->
            // 2. 결과를 종류별로 로그로 남긴다(이미 실행 중은 info, lock 확인 실패는 원인과 함께 warn).
            when (result) {
                is AiStorySummaryAlreadyRunning ->
                    log.info(
                        "Skip scheduled story summary because another tick is running: startedAt={}",
                        result.runningSummary.startedAt
                    )

                is AiStorySummaryLockUnavailable ->
                    log.warn(
                        "Skip scheduled story summary because the execution lock could not be checked",
                        result.cause
                    )

                is AiStorySummaryStarted ->
                    log.info(
                        "Scheduled story summary finished: due={}, created={}, skipped={}, failed={}, aborted={}",
                        result.result.due,
                        result.result.created,
                        result.result.skipped,
                        result.result.failed,
                        result.result.aborted
                    )
            }
        }.onFailure { error ->
            // 3. 예외는 warn 로그로 남기고 삼킨다.
            log.warn("Scheduled story summary failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AiStorySummaryScheduler::class.java)
    }
}
