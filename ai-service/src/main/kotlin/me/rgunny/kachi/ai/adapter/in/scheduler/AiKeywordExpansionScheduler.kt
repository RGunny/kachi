package me.rgunny.kachi.ai.adapter.`in`.scheduler

import jakarta.annotation.PostConstruct
import me.rgunny.kachi.ai.adapter.`in`.keyword.AiKeywordExpansionExecutionResult.*
import me.rgunny.kachi.ai.adapter.`in`.keyword.AiKeywordExpansionExecutor
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 설정된 주기마다 활성 키워드 확장을 자동 실행하는 scheduler.
 *
 * 중복 실행 방지와 유스케이스 호출은 AiKeywordExpansionExecutor에 위임한다.
 */
@Component
class AiKeywordExpansionScheduler(
    private val executor: AiKeywordExpansionExecutor,
    private val properties: AiKeywordExpansionSchedulerProperties
) {
    @PostConstruct
    fun logSchedulerProperties() {
        log.info(
            "AI keyword expansion scheduler configured: enabled={}, initialDelay={}, fixedDelay={}",
            properties.enabled,
            properties.initialDelay,
            properties.fixedDelay
        )
    }

    @Scheduled(
        fixedDelayString = AiKeywordExpansionSchedulerProperties.FIXED_DELAY_EXPRESSION,
        initialDelayString = AiKeywordExpansionSchedulerProperties.INITIAL_DELAY_EXPRESSION
    )
    suspend fun expandKeywords() {
        // 1. local/test처럼 자동 LLM 호출을 피해야 하는 환경에서는 scheduler 실행을 건너뛴다.
        if (!properties.enabled) {
            return
        }

        // 2. 요청 키워드를 비워 보내 user-service의 활성 키워드 전체를 확장 대상으로 삼는다.
        val command = ExpandKeywordsCommand(
            keywords = emptyList(),
            maxExpansionsPerKeyword = properties.maxExpansionsPerKeyword
        )

        log.info("Scheduled keyword expansion started")
        runCatching {
            executor.execute(command)
        }.onSuccess { result ->
            // 3. scheduler는 이미 실행 중인 확장을 실패로 보지 않고 이번 tick만 skip한다.
            when (result) {
                is AlreadyRunning ->
                    log.info(
                        "Skip scheduled keyword expansion because another expansion is running: startedAt={}",
                        result.runningExpansion.startedAt
                    )

                is Started ->
                    log.info(
                        "Scheduled keyword expansion finished: runId={}, status={}, requested={}, succeeded={}, failures={}",
                        result.result.runId.value,
                        result.result.status,
                        result.result.requestedKeywords,
                        result.result.succeededCount,
                        result.result.failureCount
                    )
            }
        }.onFailure { error ->
            // 4. scheduler 루프가 중단되지 않도록 예외는 로그로 남기고 삼킨다.
            log.warn("Scheduled keyword expansion failed", error)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(AiKeywordExpansionScheduler::class.java)
    }
}
