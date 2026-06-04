package me.rgunny.kachi.ai.application.service

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.news.NewsReaderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.summary.NewsHash
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class SummarizeNewsService(
    private val keywordReaderPort: KeywordReaderPort,
    private val newsReaderPort: NewsReaderPort,
    private val llmProviderPort: LlmProviderPort,
    private val newsSummaryPersistencePort: NewsSummaryPersistencePort,
    private val aiRunPersistencePort: AiRunPersistencePort,
    private val clock: Clock
) : SummarizeNewsUseCase {

    override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
        // 1. 요청 키워드가 없으면 외부 키워드 소유 서비스에서 활성 키워드를 읽는다.
        val keywords = command.keywords.ifEmpty {
            keywordReaderPort.findActiveKeywords()
        }

        // 2. AI 실행 기록을 RUNNING 상태로 먼저 저장한다.
        val startedRun = aiRunPersistencePort.save(
            AiRun.start(
                targetType = AiRunTargetType.NEWS_SUMMARY,
                requestedKeywords = keywords.size,
                startedAt = Instant.now(clock)
            )
        )

        var succeededCount = 0
        var failureCount = 0
        var failureReason: AiFailureReason? = null
        var generationMetadata: LlmGenerationMetadata? = null

        // 3. 키워드별로 수집 뉴스를 읽고 LLM 요약에 성공한 결과만 저장한다.
        for (keyword in keywords) {
            runCatching {
                val articles = newsReaderPort.findNews(
                    keyword = keyword,
                    from = command.from,
                    to = command.to,
                    limit = command.maxArticlesPerKeyword
                )
                require(articles.isNotEmpty()) { "news summary input articles are empty" }

                val llmResult = llmProviderPort.summarizeNews(
                    keyword = keyword,
                    articles = articles
                )
                val summary = NewsSummary.create(
                    keyword = keyword,
                    sourceNewsIds = articles.map { it.id },
                    newsHash = NewsHash.calculate(
                        keyword = keyword,
                        from = command.from,
                        to = command.to,
                        sourceNewsIds = articles.map { it.id }
                    ),
                    title = llmResult.title,
                    content = llmResult.content,
                    sentiment = llmResult.sentiment,
                    provider = llmResult.metadata.provider,
                    model = llmResult.metadata.model,
                    promptVersion = llmResult.metadata.promptVersion,
                    tokenUsage = llmResult.metadata.tokenUsage,
                    createdAt = Instant.now(clock)
                )
                newsSummaryPersistencePort.save(summary)
                generationMetadata = generationMetadata ?: llmResult.metadata
            }.onSuccess {
                succeededCount += 1
            }.onFailure { error ->
                failureCount += 1
                failureReason = failureReason ?: failureReasonOf(error)
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

        return SummarizeNewsResult.from(completedRun)
    }

    private fun failureReasonOf(error: Throwable): AiFailureReason {
        return when (error) {
            is IllegalArgumentException -> AiFailureReason.EMPTY_INPUT
            else -> AiFailureReason.UNKNOWN
        }
    }
}
