package me.rgunny.kachi.ai.adapter.`in`.news

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import java.time.Instant

/**
 * 현재 인스턴스에서 실행 중인 뉴스 요약 작업의 최소 메타데이터다.
 */
data class RunningAiNewsSummary(
    val startedAt: Instant
)

/**
 * 뉴스 요약 요청의 처리 상태 결과.
 */
sealed interface AiNewsSummaryExecutionResult {

    data class Started(
        val result: SummarizeNewsResult
    ) : AiNewsSummaryExecutionResult

    data class AlreadyRunning(
        val runningSummary: RunningAiNewsSummary
    ) : AiNewsSummaryExecutionResult
}
