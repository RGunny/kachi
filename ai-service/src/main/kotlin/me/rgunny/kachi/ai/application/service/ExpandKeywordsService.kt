package me.rgunny.kachi.ai.application.service

import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.`in`.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class ExpandKeywordsService(
    private val keywordReaderPort: KeywordReaderPort,
    private val llmProviderPort: LlmProviderPort,
    private val keywordExpansionPersistencePort: KeywordExpansionPersistencePort,
    private val aiRunPersistencePort: AiRunPersistencePort,
    private val clock: Clock
) : ExpandKeywordsUseCase {

    override suspend fun expand(command: ExpandKeywordsCommand): ExpandKeywordsResult {
        // 1. 요청 키워드가 없으면 외부 키워드 소유 서비스에서 활성 키워드를 읽는다.
        val keywords = command.keywords.ifEmpty {
            keywordReaderPort.findActiveKeywords()
        }

        // 2. AI 실행 기록을 RUNNING 상태로 먼저 저장한다.
        val startedRun = aiRunPersistencePort.save(
            AiRun.start(
                targetType = AiRunTargetType.KEYWORD_EXPANSION,
                requestedKeywords = keywords.size,
                startedAt = Instant.now(clock)
            )
        )

        var succeededCount = 0
        var failureCount = 0
        var failureReason: AiFailureReason? = null
        var generationMetadata: LlmGenerationMetadata? = null

        // 3. 키워드별로 LLM 확장을 호출하고 성공한 결과만 저장한다.
        for (keyword in keywords) {
            runCatching {
                val llmResult = llmProviderPort.expandKeyword(
                    keyword = keyword,
                    maxExpansions = command.maxExpansionsPerKeyword
                )
                val expansion = KeywordExpansion.create(
                    keyword = keyword,
                    expandedKeywords = llmResult.expandedKeywords,
                    provider = llmResult.metadata.provider,
                    model = llmResult.metadata.model,
                    promptVersion = llmResult.metadata.promptVersion,
                    createdAt = Instant.now(clock)
                )
                keywordExpansionPersistencePort.save(expansion)
                generationMetadata = generationMetadata ?: llmResult.metadata
            }.onSuccess {
                succeededCount += 1
            }.onFailure {
                failureCount += 1
                failureReason = failureReason ?: AiFailureReason.UNKNOWN
            }
        }

        // 4. 키워드별 성공/실패 집계로 실행 기록을 완료한다.
        val completedRun = aiRunPersistencePort.save(
            startedRun.complete(
                succeededCount = succeededCount,
                failureCount = failureCount,
                failureReason = failureReason,
                provider = generationMetadata?.provider,
                model = generationMetadata?.model,
                promptVersion = generationMetadata?.promptVersion,
                finishedAt = Instant.now(clock)
            )
        )

        return ExpandKeywordsResult.from(completedRun)
    }
}
