package me.rgunny.kachi.ai.application.service.news

import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.inbound.news.model.SummarizedNewsResult
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiSkipReason

/**
 * 키워드별 결과를 실행 기록에 남길 집계로 접은 값.
 *
 * `AiRun`은 실패 원인과 skip 사유, 생성 metadata를 각각 하나만 보관하므로
 * 여러 건 중 무엇을 대표로 남길지 정하는 규칙이 여기 있다.
 */
internal class NewsSummaryOutcome private constructor(
    val succeededCount: Int,
    val failureCount: Int,
    val skippedCount: Int,
    val failureReason: AiFailureReason?,
    val skipReason: AiSkipReason?,
    val metadata: LlmGenerationMetadata?,
    val summaries: List<SummarizedNewsResult>
) {

    companion object {

        fun of(outcomes: List<KeywordOutcome>): NewsSummaryOutcome {
            val accumulator = Accumulator()

            outcomes.forEach { outcome ->
                // subject가 sealed이므로 KeywordOutcome에 variant가 늘면 이 when이 컴파일되지 않는다.
                // 새 결과가 어느 카운트에도 잡히지 않은 채 지나가는 것을 컴파일러가 막는다.
                when (outcome) {
                    is KeywordOutcome.Succeeded -> accumulator.recordSuccess(outcome)
                    is KeywordOutcome.Failed -> accumulator.recordFailure(outcome)
                    is KeywordOutcome.Skipped -> accumulator.recordSkip(outcome)
                }
            }

            return accumulator.toOutcome()
        }
    }

    /**
     * 한 번의 순회로 집계를 모은다.
     */
    private class Accumulator {
        private val summaries = mutableListOf<SummarizedNewsResult>()
        private var failureCount = 0
        private var skippedCount = 0
        private var failureReason: AiFailureReason? = null
        private var skipReason: AiSkipReason? = null
        private var metadata: LlmGenerationMetadata? = null

        fun recordSuccess(outcome: KeywordOutcome.Succeeded) {
            summaries += outcome.summary
            // 실행에 여러 provider가 섞일 수 있으므로 최초 성공분을 대표로 남긴다.
            metadata = metadata ?: outcome.metadata
        }

        fun recordFailure(outcome: KeywordOutcome.Failed) {
            failureCount += 1
            // 실패 원인은 하나만 남으므로 가장 먼저 발생한 것을 대표로 쓴다.
            failureReason = failureReason ?: outcome.reason
        }

        fun recordSkip(outcome: KeywordOutcome.Skipped) {
            skippedCount += 1

            // NO_INPUT은 조치할 것이 없지만 PROVIDER_UNAVAILABLE은 장애 신호다. 조치가 필요한 쪽을 대표로 남긴다.
            if (skipReason == null || outcome.reason == AiSkipReason.PROVIDER_UNAVAILABLE) {
                skipReason = outcome.reason
            }
        }

        fun toOutcome(): NewsSummaryOutcome {
            return NewsSummaryOutcome(
                succeededCount = summaries.size,
                failureCount = failureCount,
                skippedCount = skippedCount,
                failureReason = failureReason,
                skipReason = skipReason,
                metadata = metadata,
                summaries = summaries.toList()
            )
        }
    }
}
