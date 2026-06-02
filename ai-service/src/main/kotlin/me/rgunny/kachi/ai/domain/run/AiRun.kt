package me.rgunny.kachi.ai.domain.run

import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import java.time.Instant

class AiRun private constructor(
    val id: AiRunId,
    val targetType: AiRunTargetType,
    val status: AiRunStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int,
    val failureReason: AiFailureReason?,
    val provider: LlmProviderName?,
    val model: LlmModelName?,
    val promptVersion: PromptVersion?
) {

    companion object {

        fun start(
            targetType: AiRunTargetType,
            requestedKeywords: Int,
            startedAt: Instant
        ): AiRun {
            require(requestedKeywords >= 0) { "요청 키워드 수는 0 이상이어야 합니다" }

            return AiRun(
                id = AiRunId.newId(),
                targetType = targetType,
                status = AiRunStatus.RUNNING,
                startedAt = startedAt,
                finishedAt = null,
                requestedKeywords = requestedKeywords,
                succeededCount = 0,
                failureCount = 0,
                failureReason = null,
                provider = null,
                model = null,
                promptVersion = null
            )
        }

        fun restore(
            id: AiRunId,
            targetType: AiRunTargetType,
            status: AiRunStatus,
            startedAt: Instant,
            finishedAt: Instant?,
            requestedKeywords: Int,
            succeededCount: Int,
            failureCount: Int,
            failureReason: AiFailureReason?,
            provider: LlmProviderName?,
            model: LlmModelName?,
            promptVersion: PromptVersion?
        ): AiRun {
            return AiRun(
                id = id,
                targetType = targetType,
                status = status,
                startedAt = startedAt,
                finishedAt = finishedAt,
                requestedKeywords = requestedKeywords,
                succeededCount = succeededCount,
                failureCount = failureCount,
                failureReason = failureReason,
                provider = provider,
                model = model,
                promptVersion = promptVersion
            )
        }
    }

    fun complete(
        succeededCount: Int,
        failureCount: Int,
        failureReason: AiFailureReason?,
        provider: LlmProviderName?,
        model: LlmModelName?,
        promptVersion: PromptVersion?,
        finishedAt: Instant
    ): AiRun {
        require(status == AiRunStatus.RUNNING) { "RUNNING 상태의 AI 실행만 완료할 수 있습니다" }
        require(!finishedAt.isBefore(startedAt)) { "완료 시각은 시작 시각보다 이전일 수 없습니다" }
        require(succeededCount >= 0) { "성공 처리 건수는 0 이상이어야 합니다" }
        require(failureCount >= 0) { "실패 처리 건수는 0 이상이어야 합니다" }

        val completedStatus = when {
            succeededCount > 0 && failureCount == 0 -> AiRunStatus.SUCCEEDED
            succeededCount > 0 && failureCount > 0 -> AiRunStatus.PARTIALLY_FAILED
            else -> AiRunStatus.FAILED
        }

        val completedFailureReason = when (completedStatus) {
            AiRunStatus.SUCCEEDED -> null
            else -> failureReason ?: AiFailureReason.UNKNOWN
        }

        return AiRun(
            id = id,
            targetType = targetType,
            status = completedStatus,
            startedAt = startedAt,
            finishedAt = finishedAt,
            requestedKeywords = requestedKeywords,
            succeededCount = succeededCount,
            failureCount = failureCount,
            failureReason = completedFailureReason,
            provider = provider,
            model = model,
            promptVersion = promptVersion
        )

    }
}
