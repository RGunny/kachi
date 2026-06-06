package me.rgunny.kachi.ai.adapter.`in`.keyword

import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsResult
import java.time.Instant

/**
 * 현재 인스턴스에서 실행 중인 키워드 확장 작업의 최소 메타데이터다.
 */
data class RunningAiKeywordExpansion(
    val startedAt: Instant
)

/**
 * 키워드 확장 요청의 처리 상태 결과.
 *
 * sealed interface로 가능한 결과를 닫아 controller/scheduler 호출자가 when 분기에서
 * 모든 처리 결과를 명시적으로 다루도록 한다.
 */
sealed interface AiKeywordExpansionExecutionResult {

    data class Started(
        val result: ExpandKeywordsResult
    ) : AiKeywordExpansionExecutionResult

    data class AlreadyRunning(
        val runningExpansion: RunningAiKeywordExpansion
    ) : AiKeywordExpansionExecutionResult
}
