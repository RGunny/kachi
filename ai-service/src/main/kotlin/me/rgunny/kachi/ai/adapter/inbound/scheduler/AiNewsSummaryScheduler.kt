package me.rgunny.kachi.ai.adapter.inbound.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryStarted
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizeNewsCommand
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 뉴스 요약을 자동 실행하는 scheduler.
 *
 * 요약 구간은 저장된 watermark에서 정해지므로 이 계층은 구간 정책만 전달하고, 실제 계산과 전진은 유스케이스가 맡는다.
 * 중복 실행 방지와 유스케이스 호출은 AiNewsSummaryExecutor에 위임한다.
 */
@Component
class AiNewsSummaryScheduler(
    private val executor: AiNewsSummaryExecutor,
    private val properties: AiNewsSummarySchedulerProperties
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "AI news summary scheduler configured: enabled={}, initialDelay={}, fixedDelay={}, overlap={}, maxLookback={}",
            properties.enabled,
            properties.initialDelay,
            properties.fixedDelay,
            properties.overlap,
            properties.maxLookback
        )
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

        // 2. 마지막으로 처리를 끝낸 지점에서 이어받도록 요청한다.
        // 실행이 지연되거나 서비스가 멈춰 있었어도 그 구간이 다음 실행에 그대로 들어온다.
        val command = SummarizeNewsCommand(
            keywords = emptyList(),
            window = properties.toWindowRequest(),
            maxArticlesPerKeyword = properties.maxArticlesPerKeyword
        )

        log.info(
            "Scheduled news summary started: overlap={}, maxLookback={}",
            properties.overlap,
            properties.maxLookback
        )
        runCatching {
            executor.execute(command)
        }.onSuccess { result ->
            // 3. scheduler는 이미 실행 중인 요약을 실패로 보지 않고 이번 tick만 skip한다.
            when (result) {
                is AiNewsSummaryAlreadyRunning ->
                    log.info(
                        "Skip scheduled news summary because another summary is running: startedAt={}",
                        result.runningSummary.startedAt
                    )

                is AiNewsSummaryStarted ->
                    log.info(
                        "Scheduled news summary finished: runId={}, status={}, requested={}, succeeded={}, failures={}, window={}~{}, watermarkAdvanced={}",
                        result.result.runId.value,
                        result.result.status,
                        result.result.requestedKeywords,
                        result.result.succeededCount,
                        result.result.failureCount,
                        result.result.windowFrom,
                        result.result.windowTo,
                        result.result.watermarkAdvanced
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
