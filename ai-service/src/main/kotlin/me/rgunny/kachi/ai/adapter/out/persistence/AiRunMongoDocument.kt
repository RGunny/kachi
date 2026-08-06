package me.rgunny.kachi.ai.adapter.out.persistence

import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunId
import me.rgunny.kachi.ai.domain.run.AiRunStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant
import java.util.UUID

@Document(collection = "ai_runs")
data class AiRunMongoDocument(
    @Id
    val id: UUID,
    val targetType: AiRunTargetType,
    @Indexed
    val status: AiRunStatus,
    @Indexed
    val startedAt: Instant,
    val finishedAt: Instant?,
    val requestedKeywords: Int,
    val succeededCount: Int,
    val failureCount: Int,
    val failureReason: AiFailureReason?,
    val provider: String?,
    val model: String?,
    val promptVersion: String?,
    val windowFrom: Instant?,
    val windowTo: Instant?,
    val watermarkAdvanced: Boolean
) {

    fun toDomain(): AiRun {
        return AiRun.restore(
            id = AiRunId.of(id),
            targetType = targetType,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            requestedKeywords = requestedKeywords,
            succeededCount = succeededCount,
            failureCount = failureCount,
            failureReason = failureReason,
            provider = provider?.let(LlmProviderName::of),
            model = model?.let(LlmModelName::of),
            promptVersion = promptVersion?.let(PromptVersion::of),
            windowFrom = windowFrom,
            windowTo = windowTo,
            watermarkAdvanced = watermarkAdvanced
        )
    }

    companion object {
        fun fromDomain(aiRun: AiRun): AiRunMongoDocument {
            return AiRunMongoDocument(
                id = aiRun.id.value,
                targetType = aiRun.targetType,
                status = aiRun.status,
                startedAt = aiRun.startedAt,
                finishedAt = aiRun.finishedAt,
                requestedKeywords = aiRun.requestedKeywords,
                succeededCount = aiRun.succeededCount,
                failureCount = aiRun.failureCount,
                failureReason = aiRun.failureReason,
                provider = aiRun.provider?.value,
                model = aiRun.model?.value,
                promptVersion = aiRun.promptVersion?.value,
                windowFrom = aiRun.windowFrom,
                windowTo = aiRun.windowTo,
                watermarkAdvanced = aiRun.watermarkAdvanced
            )
        }
    }
}
