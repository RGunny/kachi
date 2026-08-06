package me.rgunny.kachi.ai.application.service

import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizedNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.application.port.`in`.news.SummaryWindowRequest
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.news.NewsReaderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.SummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.config.KeywordQuarantineProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.summary.NewsHash
import me.rgunny.kachi.ai.domain.summary.NewsSummary
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import me.rgunny.kachi.ai.domain.watermark.SummaryWatermark
import me.rgunny.kachi.ai.domain.watermark.SummaryWindow
import org.slf4j.LoggerFactory
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
    private val summaryWatermarkPersistencePort: SummaryWatermarkPersistencePort,
    private val keywordQuarantinePersistencePort: KeywordQuarantinePersistencePort,
    private val quarantineProperties: KeywordQuarantineProperties,
    private val clock: Clock
) : SummarizeNewsUseCase {

    override suspend fun summarize(command: SummarizeNewsCommand): SummarizeNewsResult {
        val now = Instant.now(clock)

        // 1. 격리 기록을 한 번만 읽어 대상 제외와 실패 누적에 함께 쓴다.
        val quarantines = loadQuarantines()
        val keywords = excludeQuarantined(resolveKeywords(command), quarantines)

        // 2. 이번 실행이 처리할 구간을 확정한다. watermark 기반일 때만 전진 대상이 생긴다.
        val watermark = loadWatermark(command.window)
        val window = resolveWindow(command.window, watermark?.position, now)

        // 3. 실행 기록을 RUNNING 상태로 먼저 남기고 키워드별로 요약한다.
        val startedRun = aiRunPersistencePort.save(
            AiRun.start(
                targetType = TARGET_TYPE,
                requestedKeywords = keywords.size,
                startedAt = now,
                windowFrom = window.from,
                windowTo = window.to
            )
        )
        val outcome = NewsSummaryOutcome.of(
            keywords.map { keyword ->
                summarizeKeyword(keyword, window, command.maxArticlesPerKeyword, quarantines[keyword], now)
            }
        )

        // 4. 격리 제외 키워드가 모두 성공했을 때만 watermark를 전진시킨다.
        // 실패가 있으면 그대로 두어 다음 실행이 같은 구간을 다시 처리하고, 성공분은 newsHash로 재사용한다.
        val watermarkAdvanced = advanceWatermarkIfCompleted(
            request = command.window,
            watermark = watermark,
            window = window,
            failureCount = outcome.failureCount,
            now = now
        )

        return SummarizeNewsResult.from(
            completeRun(startedRun, outcome, watermarkAdvanced),
            summaries = outcome.summaries
        )
    }

    /**
     * 요청 키워드가 없으면 외부 키워드 소유 서비스에서 활성 키워드를 읽는다.
     */
    private suspend fun resolveKeywords(command: SummarizeNewsCommand): List<AiKeyword> {
        return command.keywords.ifEmpty {
            keywordReaderPort.findActiveKeywords()
        }
    }

    private suspend fun loadQuarantines(): Map<AiKeyword, KeywordQuarantine> {
        return keywordQuarantinePersistencePort
            .findAllBy(TARGET_TYPE)
            .associateBy { it.keyword }
    }

    private fun excludeQuarantined(
        keywords: List<AiKeyword>,
        quarantines: Map<AiKeyword, KeywordQuarantine>
    ): List<AiKeyword> {
        val targets = keywords.filterNot { quarantines[it]?.isQuarantined == true }

        if (targets.size < keywords.size) {
            log.info(
                "Excluded quarantined keywords from news summary: requested={}, excluded={}",
                keywords.size,
                keywords.size - targets.size
            )
        }

        return targets
    }

    private suspend fun loadWatermark(request: SummaryWindowRequest): SummaryWatermark? {
        return when (request) {
            is SummaryWindowRequest.FromWatermark -> summaryWatermarkPersistencePort.findBy(TARGET_TYPE)
            is SummaryWindowRequest.Explicit -> null
        }
    }

    /**
     * 키워드 하나를 요약하고 결과에 따라 연속 실패 누적을 갱신한다.
     *
     * 키워드 하나의 실패가 나머지 키워드를 막지 않도록 예외를 여기서 가둔다.
     */
    private suspend fun summarizeKeyword(
        keyword: AiKeyword,
        window: ResolvedWindow,
        maxArticlesPerKeyword: Int,
        quarantine: KeywordQuarantine?,
        now: Instant
    ): Result<SummarizedKeyword> {
        return runCatching {
            summarizeCollectedNews(keyword, window, maxArticlesPerKeyword)
        }.onSuccess {
            resetKeywordFailures(quarantine, now)
        }.onFailure { error ->
            recordKeywordFailure(quarantine, keyword, failureReasonOf(error), now)
        }
    }

    private suspend fun summarizeCollectedNews(
        keyword: AiKeyword,
        window: ResolvedWindow,
        maxArticlesPerKeyword: Int
    ): SummarizedKeyword {
        val articles = newsReaderPort.findNews(
            keyword = keyword,
            from = window.from,
            to = window.to,
            limit = maxArticlesPerKeyword
        )
        require(articles.isNotEmpty()) { "news summary input articles are empty" }

        // newsHash는 같은 keyword/news id 묶음을 식별하는 LLM 호출 전 cache key다.
        // 조회 구간은 실행마다 달라지므로 hash에 넣지 않는다. 그래야 실패 후 재시도에서 성공분을 재사용한다.
        val newsHash = NewsHash.calculate(
            keyword = keyword,
            sourceNewsIds = articles.map { it.id }
        )
        val preparedLlm = llmProviderPort.prepareNewsSummary()
        val existingSummary = newsSummaryPersistencePort.findByUniqueKey(
            keyword = keyword,
            newsHash = newsHash,
            promptVersion = preparedLlm.plan.promptVersion,
            model = preparedLlm.plan.model
        )

        return existingSummary?.let(::reuseSummary)
            ?: generateSummary(keyword, articles, newsHash, preparedLlm)
    }

    /**
     * 기존 요약을 재사용하면 이번 실행에서는 LLM을 호출하지 않으므로 token 사용량도 0으로 남긴다.
     */
    private fun reuseSummary(existingSummary: NewsSummary): SummarizedKeyword {
        return SummarizedKeyword(
            summary = SummarizedNewsResult.from(existingSummary, reused = true),
            metadata = LlmGenerationMetadata(
                provider = existingSummary.provider,
                model = existingSummary.model,
                promptVersion = existingSummary.promptVersion,
                tokenUsage = TokenUsage(inputTokens = 0, outputTokens = 0)
            )
        )
    }

    private suspend fun generateSummary(
        keyword: AiKeyword,
        articles: List<NewsArticle>,
        newsHash: String,
        preparedLlm: PreparedLlmNewsSummary
    ): SummarizedKeyword {
        val llmResult = preparedLlm.summarize(
            keyword = keyword,
            articles = articles
        )
        val summary = NewsSummary.create(
            keyword = keyword,
            sourceNewsIds = articles.map { it.id },
            newsHash = newsHash,
            title = llmResult.title,
            content = llmResult.content,
            sentiment = llmResult.sentiment,
            provider = llmResult.metadata.provider,
            model = llmResult.metadata.model,
            promptVersion = llmResult.metadata.promptVersion,
            tokenUsage = llmResult.metadata.tokenUsage,
            createdAt = Instant.now(clock)
        )
        // 선조회 이후 다른 요청이 먼저 저장했으면, 새로 저장하지 않고 기존 요약을 사용한다.
        val savedSummary = newsSummaryPersistencePort.saveOrFindExisting(summary)

        return SummarizedKeyword(
            summary = SummarizedNewsResult.from(savedSummary, reused = savedSummary.id != summary.id),
            metadata = llmResult.metadata
        )
    }

    private suspend fun completeRun(
        startedRun: AiRun,
        outcome: NewsSummaryOutcome,
        watermarkAdvanced: Boolean
    ): AiRun {
        return aiRunPersistencePort.save(
            startedRun.complete(
                succeededCount = outcome.succeededCount,
                failureCount = outcome.failureCount,
                failureReason = outcome.failureReason,
                provider = outcome.metadata?.provider,
                model = outcome.metadata?.model,
                promptVersion = outcome.metadata?.promptVersion,
                finishedAt = Instant.now(clock),
                watermarkAdvanced = watermarkAdvanced
            )
        )
    }

    private fun resolveWindow(
        request: SummaryWindowRequest,
        watermark: Instant?,
        now: Instant
    ): ResolvedWindow {
        return when (request) {
            // 수동 실행은 지정 구간을 그대로 쓴다. 지정하지 않으면 collector가 전체 기간을 조회한다.
            is SummaryWindowRequest.Explicit -> ResolvedWindow(from = request.from, to = request.to)

            is SummaryWindowRequest.FromWatermark -> {
                val resolved = SummaryWindow.resolve(
                    watermark = watermark,
                    now = now,
                    overlap = request.overlap,
                    maxLookback = request.maxLookback
                )

                // maxLookback 하한에 걸리면 그 앞 구간은 어느 실행에도 들어가지 않는다. 조용히 사라지지 않게 남긴다.
                if (resolved.truncated) {
                    log.warn(
                        "News summary window truncated by max lookback. Skipped range is not processed: skippedFrom={}, windowFrom={}, watermark={}",
                        resolved.skippedFrom,
                        resolved.from,
                        watermark
                    )
                }

                ResolvedWindow(from = resolved.from, to = resolved.to)
            }
        }
    }

    private suspend fun advanceWatermarkIfCompleted(
        request: SummaryWindowRequest,
        watermark: SummaryWatermark?,
        window: ResolvedWindow,
        failureCount: Int,
        now: Instant
    ): Boolean {
        // 임의 구간을 지정한 수동 실행이 watermark를 움직이면 지정하지 않은 구간까지 처리된 것으로 기록된다.
        if (request !is SummaryWindowRequest.FromWatermark) {
            return false
        }
        if (failureCount > 0) {
            return false
        }

        val advanceTo = window.to ?: return false
        val advanced = if (watermark == null) {
            SummaryWatermark.initial(targetType = TARGET_TYPE, position = advanceTo, updatedAt = now)
        } else {
            // 전진하지 않았으면 저장하지 않고 그대로 기록한다. 이 경로는 시각 역행이나 미래 watermark 신호다.
            watermark.advanceTo(advanceTo, now) ?: run {
                log.warn(
                    "Watermark not advanced because requested position is not after current: requested={}, current={}",
                    advanceTo,
                    watermark.position
                )
                return false
            }
        }
        summaryWatermarkPersistencePort.save(advanced)

        return true
    }

    private suspend fun recordKeywordFailure(
        quarantine: KeywordQuarantine?,
        keyword: AiKeyword,
        reason: AiFailureReason,
        now: Instant
    ) {
        val tracked = quarantine ?: KeywordQuarantine.track(
            targetType = TARGET_TYPE,
            keyword = keyword,
            updatedAt = now
        )
        val updated = tracked.recordFailure(
            reason = reason,
            failureThreshold = quarantineProperties.failureThreshold,
            updatedAt = now
        )
        keywordQuarantinePersistencePort.save(updated)

        // TODO: 관리자 알림 연계는 notification 파이프라인이 붙은 뒤 연결한다.
        if (updated.isQuarantined && !tracked.isQuarantined) {
            log.error(
                "Keyword quarantined after consecutive news summary failures: keyword={}, consecutiveFailures={}, lastFailureReason={}",
                keyword.value,
                updated.consecutiveFailures,
                reason
            )
        }
    }

    private suspend fun resetKeywordFailures(quarantine: KeywordQuarantine?, now: Instant) {
        // 실패한 적 없는 키워드까지 실행마다 기록하지 않는다.
        if (quarantine == null || !quarantine.needsReset()) {
            return
        }

        keywordQuarantinePersistencePort.save(quarantine.recordSuccess(now))
    }

    /**
     * 확정된 조회 구간. 수동 실행은 구간을 지정하지 않을 수 있어 null을 허용한다.
     */
    private data class ResolvedWindow(
        val from: Instant?,
        val to: Instant?
    )

    /**
     * 키워드 하나의 요약 결과.
     *
     * 실행 기록에는 이번 실행이 어떤 provider/model을 썼는지도 남아야 하므로 생성 메타데이터를 함께 들고 간다.
     */
    private data class SummarizedKeyword(
        val summary: SummarizedNewsResult,
        val metadata: LlmGenerationMetadata
    )

    /**
     * 키워드별 결과를 실행 기록에 남길 집계로 접은 값.
     */
    private class NewsSummaryOutcome(
        val succeededCount: Int,
        val failureCount: Int,
        val failureReason: AiFailureReason?,
        val metadata: LlmGenerationMetadata?,
        val summaries: List<SummarizedNewsResult>
    ) {
        companion object {
            fun of(results: List<Result<SummarizedKeyword>>): NewsSummaryOutcome {
                val succeeded = results.mapNotNull { it.getOrNull() }

                return NewsSummaryOutcome(
                    succeededCount = succeeded.size,
                    failureCount = results.size - succeeded.size,
                    // 실행 기록에는 실패 원인을 하나만 남기므로 가장 먼저 발생한 것을 대표로 쓴다.
                    failureReason = results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let(::failureReasonOf),
                    metadata = succeeded.firstOrNull()?.metadata,
                    summaries = succeeded.map { it.summary }
                )
            }
        }
    }

    private companion object {
        val TARGET_TYPE = AiRunTargetType.NEWS_SUMMARY
        val log = LoggerFactory.getLogger(SummarizeNewsService::class.java)

        fun failureReasonOf(error: Throwable): AiFailureReason {
            return when (error) {
                is IllegalArgumentException -> AiFailureReason.EMPTY_INPUT
                else -> AiFailureReason.UNKNOWN
            }
        }
    }
}
