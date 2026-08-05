package me.rgunny.kachi.ai.adapter.`in`.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.ai.adapter.`in`.news.AiNewsSummaryExecutionResult
import me.rgunny.kachi.ai.adapter.`in`.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * 설정된 주기마다 뉴스 요약을 자동 실행하는 scheduler.
 *
 * 요약 대상 기간만 이 계층에서 정하고, 중복 실행 방지와 유스케이스 호출은 AiNewsSummaryExecutor에 위임한다.
 */
@Component
class AiNewsSummaryScheduler(
    private val executor: AiNewsSummaryExecutor,
    private val properties: AiNewsSummarySchedulerProperties,
    private val clock: Clock
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "AI news summary scheduler configured: enabled={}, initialDelay={}, fixedDelay={}, lookback={}",
            properties.enabled,
            properties.initialDelay,
            properties.fixedDelay,
            properties.lookback
        )

        // lookback이 실행 주기보다 짧으면 tick 사이 뉴스가 어느 요약에도 들어가지 않으므로 설정 오류를 드러낸다.
        if (properties.hasWindowGap()) {
            log.warn(
                "AI news summary lookback is shorter than fixed delay. News collected between ticks can be missed: lookback={}, fixedDelay={}",
                properties.lookback,
                properties.fixedDelay
            )
        }
    }

    @Scheduled(
        fixedDelayString = AiNewsSummarySchedulerProperties.FIXED_DELAY_EXPRESSION,
        initialDelayString = AiNewsSummarySchedulerProperties.INITIAL_DELAY_EXPRESSION
    )
    suspend fun summarizeNews() {
        // 1. local/test처럼 자동 LLM 호출을 피해야 하는 환경에서는 scheduler 실행을 건너뛴다.
        if (!properties.enabled) {
            return
        }

        // 2. 이번 tick이 요약할 뉴스 수집 기간을 확정한다.
        // lookback을 실행 주기보다 길게 두어 겹치게 조회하고, 겹침으로 생기는 중복 요약은 newsHash 재사용이 막는다.
        val to = Instant.now(clock)
        val from = to.minus(properties.lookback)
        val command = SummarizeNewsCommand(
            keywords = emptyList(),
            from = from,
            to = to,
            maxArticlesPerKeyword = properties.maxArticlesPerKeyword
        )

        log.info("Scheduled news summary started: from={}, to={}", from, to)
        runCatching {
            executor.execute(command)
        }.onSuccess { result ->
            // 3. scheduler는 이미 실행 중인 요약을 실패로 보지 않고 이번 tick만 skip한다.
            when (result) {
                is AiNewsSummaryExecutionResult.AlreadyRunning ->
                    log.info(
                        "Skip scheduled news summary because another summary is running: startedAt={}",
                        result.runningSummary.startedAt
                    )

                is AiNewsSummaryExecutionResult.Started ->
                    log.info(
                        "Scheduled news summary finished: runId={}, status={}, requested={}, succeeded={}, failures={}",
                        result.result.runId.value,
                        result.result.status,
                        result.result.requestedKeywords,
                        result.result.succeededCount,
                        result.result.failureCount
                    )
            }
        }.onFailure { error ->
            // 4. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
            log.warn("Scheduled news summary failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AiNewsSummaryScheduler::class.java)
    }
}
