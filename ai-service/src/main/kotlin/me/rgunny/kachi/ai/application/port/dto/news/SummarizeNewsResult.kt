package me.rgunny.kachi.ai.application.port.dto.news

import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.run.AiSkipReason
import java.time.Instant

/**
 * 뉴스 요약 실행 결과.
 *
 * 처리 건수를 성공/실패/skip 셋으로 나눠 담는다. skip은 조치할 것이 없는 결과이므로
 * 실패와 합치면 실행 하나를 보고 조치가 필요한지 판단할 수 없다(ADR 021).
 */
data class SummarizeNewsResult(
    val runId: AiRunId,
    val status: AiRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int,
    val skippedCount: Int,
    val skipReason: AiSkipReason?,
    val windowFrom: Instant?,
    val windowTo: Instant?,
    val watermarkAdvanced: Boolean,
    val summaries: List<SummarizedNewsResult>
) {
    companion object {
        fun from(
            aiRun: AiRun,
            summaries: List<SummarizedNewsResult> = emptyList()
        ): SummarizeNewsResult {
            return SummarizeNewsResult(
                runId = aiRun.id,
                status = aiRun.status,
                startedAt = aiRun.startedAt,
                finishedAt = aiRun.finishedAt,
                requestedKeywords = aiRun.requestedKeywords,
                succeededCount = aiRun.succeededCount,
                failureCount = aiRun.failureCount,
                skippedCount = aiRun.skippedCount,
                skipReason = aiRun.skipReason,
                windowFrom = aiRun.windowFrom,
                windowTo = aiRun.windowTo,
                watermarkAdvanced = aiRun.watermarkAdvanced,
                summaries = summaries
            )
        }
    }
}
