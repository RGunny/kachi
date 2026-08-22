package me.rgunny.kachi.ai.application.service.keyword

import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsCommand
import me.rgunny.kachi.ai.application.port.inbound.keyword.model.ExpandKeywordsResult
import me.rgunny.kachi.ai.application.port.inbound.keyword.ExpandKeywordsUseCase
import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.outbound.persistence.KeywordExpansionPersistencePort
import me.rgunny.kachi.ai.config.LlmPromptVersions
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.KeywordExpansion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
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
    private val promptVersions: LlmPromptVersions,
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

        // 3. 키워드별로 기존 확장을 재사용하거나 LLM 확장을 호출해 저장한다.
        for (keyword in keywords) {
            runCatching {
                val metadata = expandKeyword(keyword, command.maxExpansionsPerKeyword)
                generationMetadata = generationMetadata ?: metadata
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

    /**
     * 키워드 하나를 확장하고 이번 실행에 쓰인 생성 metadata를 돌려준다.
     *
     * 같은 keyword/promptVersion의 확장이 이미 있으면 LLM을 호출하지 않는다.
     * 저장 키에 model이 없으므로 다른 provider가 만든 확장도 그대로 재사용한다.
     */
    private suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmGenerationMetadata {
        val existingExpansion = keywordExpansionPersistencePort.findByUniqueKey(
            keyword = keyword,
            promptVersion = promptVersions.keywordExpansion
        )

        return existingExpansion?.let(::reuseMetadata)
            ?: generateExpansion(keyword, maxExpansions)
    }

    /**
     * 기존 확장을 재사용하면 이번 실행에서는 LLM을 호출하지 않으므로 token 사용량도 0으로 남긴다.
     */
    private fun reuseMetadata(existingExpansion: KeywordExpansion): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = existingExpansion.provider,
            model = existingExpansion.model,
            promptVersion = existingExpansion.promptVersion,
            tokenUsage = TokenUsage(inputTokens = 0, outputTokens = 0)
        )
    }

    private suspend fun generateExpansion(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmGenerationMetadata {
        val llmResult = llmProviderPort.expandKeyword(
            keyword = keyword,
            maxExpansions = maxExpansions
        )
        val expansion = KeywordExpansion.create(
            keyword = keyword,
            expandedKeywords = llmResult.expandedKeywords,
            provider = llmResult.metadata.provider,
            model = llmResult.metadata.model,
            promptVersion = llmResult.metadata.promptVersion,
            createdAt = Instant.now(clock)
        )
        // 선조회 이후 다른 요청이 먼저 저장했으면, 새로 저장하지 않고 기존 확장을 사용한다.
        keywordExpansionPersistencePort.saveOrFindExisting(expansion)

        return llmResult.metadata
    }
}
