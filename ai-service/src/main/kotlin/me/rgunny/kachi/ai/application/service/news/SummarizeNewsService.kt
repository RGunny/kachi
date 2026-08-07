package me.rgunny.kachi.ai.application.service.news

import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.ai.application.exception.KeywordReaderException
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.exception.NewsReaderException
import me.rgunny.kachi.ai.application.port.dto.news.SummarizeNewsCommand
import me.rgunny.kachi.ai.application.port.dto.news.SummarizeNewsResult
import me.rgunny.kachi.ai.application.port.dto.news.SummarizedNewsResult
import me.rgunny.kachi.ai.application.port.`in`.news.SummarizeNewsUseCase
import me.rgunny.kachi.ai.application.port.dto.news.SummaryWindowRequest
import me.rgunny.kachi.ai.application.port.out.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.application.port.dto.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.application.port.out.news.NewsReaderPort
import me.rgunny.kachi.ai.application.port.out.persistence.AiRunPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.KeywordQuarantinePersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.NewsSummaryPersistencePort
import me.rgunny.kachi.ai.application.port.out.persistence.SummaryWatermarkPersistencePort
import me.rgunny.kachi.ai.config.KeywordQuarantineProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCategory
import me.rgunny.kachi.ai.domain.llm.LlmFailureSource
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantine
import me.rgunny.kachi.ai.domain.run.AiFailureReason
import me.rgunny.kachi.ai.domain.run.AiRun
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import me.rgunny.kachi.ai.domain.run.AiSkipReason
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
            summarizeKeywords(keywords, window, command.maxArticlesPerKeyword, quarantines, now)
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
     * 키워드를 순서대로 요약한다.
     *
     * 전역 LLM 장애를 만나면 남은 키워드는 호출하지 않고 건너뛴다.
     * 장애 중에 같은 호출을 반복해도 결과는 같고 provider 압력만 올라간다.
     * watermark는 실패 때문에 유지되므로, 건너뛴 키워드는 다음 실행이 그대로 다시 처리한다.
     */
    private suspend fun summarizeKeywords(
        keywords: List<AiKeyword>,
        window: ResolvedSummaryWindow,
        maxArticlesPerKeyword: Int,
        quarantines: Map<AiKeyword, KeywordQuarantine>,
        now: Instant
    ): List<KeywordOutcome> {
        val outcomes = mutableListOf<KeywordOutcome>()
        var aborted = false

        for (keyword in keywords) {
            if (aborted) {
                outcomes += KeywordOutcome.Skipped(AiSkipReason.PROVIDER_UNAVAILABLE)
                continue
            }

            val outcome = summarizeKeyword(keyword, window, maxArticlesPerKeyword, quarantines[keyword], now)
            outcomes += outcome

            if (outcome is KeywordOutcome.Failed && outcome.abortsRun) {
                log.warn(
                    "Aborting remaining keywords in this run by global LLM failure: keyword={}, reason={}, remaining={}",
                    keyword.value,
                    outcome.reason,
                    keywords.size - outcomes.size
                )
                aborted = true
            }
        }

        return outcomes
    }

    /**
     * 키워드 하나를 요약하고 결과에 따라 연속 실패 누적을 갱신한다.
     *
     * 키워드 하나의 실패가 나머지 키워드를 막지 않도록 예외를 여기서 가둔다.
     */
    private suspend fun summarizeKeyword(
        keyword: AiKeyword,
        window: ResolvedSummaryWindow,
        maxArticlesPerKeyword: Int,
        quarantine: KeywordQuarantine?,
        now: Instant
    ): KeywordOutcome {
        return try {
            val articles = newsReaderPort.findNews(
                keyword = keyword,
                from = window.from,
                to = window.to,
                limit = maxArticlesPerKeyword
            )

            // 요약할 뉴스가 없는 것은 장애가 아니다. 실패로 세면 뉴스가 뜸한 키워드 하나가 watermark 전체를 붙잡는다.
            if (articles.isEmpty()) {
                return KeywordOutcome.Skipped(AiSkipReason.NO_INPUT)
            }

            val succeeded = summarizeCollectedNews(keyword, articles)
            resetKeywordFailures(quarantine, now)

            succeeded
        } catch (error: CancellationException) {
            // coroutine 취소는 요약 실패가 아니므로 결과로 변환하지 않는다.
            throw error
        } catch (error: Exception) {
            recordFailedKeyword(quarantine, keyword, error, now)
        }
    }

    /**
     * 실패를 분류해 실행 기록에 남길 원인을 정하고, 키워드 귀속 실패일 때만 격리 카운트를 올린다.
     *
     * rate limit이나 timeout은 다음 실행이 같은 구간을 다시 처리하면 해소된다.
     * 이런 실패까지 카운트하면 provider 장애 몇 번으로 정상 키워드가 영구 격리된다.
     */
    private suspend fun recordFailedKeyword(
        quarantine: KeywordQuarantine?,
        keyword: AiKeyword,
        error: Exception,
        now: Instant
    ): KeywordOutcome.Failed {
        val failure = (error as? LlmProviderException)?.failure
        val reason = failureReasonOf(error)

        if (keywordBound(error)) {
            recordKeywordFailure(quarantine, keyword, reason, now)
        } else {
            log.warn(
                "News summary failed by infrastructure error. Keyword quarantine counter is not updated: keyword={}, reason={}",
                keyword.value,
                reason,
                error
            )
        }

        return KeywordOutcome.Failed(
            reason = reason,
            abortsRun = failure != null && abortsRun(failure)
        )
    }

    private suspend fun summarizeCollectedNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): KeywordOutcome.Succeeded {
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
    private fun reuseSummary(existingSummary: NewsSummary): KeywordOutcome.Succeeded {
        return KeywordOutcome.Succeeded(
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
    ): KeywordOutcome.Succeeded {
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

        return KeywordOutcome.Succeeded(
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
                skippedCount = outcome.skippedCount,
                skipReason = outcome.skipReason,
                watermarkAdvanced = watermarkAdvanced
            )
        )
    }

    private fun resolveWindow(
        request: SummaryWindowRequest,
        watermark: Instant?,
        now: Instant
    ): ResolvedSummaryWindow {
        return when (request) {
            // 수동 실행은 지정 구간을 그대로 쓴다. 지정하지 않으면 collector가 전체 기간을 조회한다.
            is SummaryWindowRequest.Explicit -> ResolvedSummaryWindow(from = request.from, to = request.to)

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

                ResolvedSummaryWindow(from = resolved.from, to = resolved.to)
            }
        }
    }

    private suspend fun advanceWatermarkIfCompleted(
        request: SummaryWindowRequest,
        watermark: SummaryWatermark?,
        window: ResolvedSummaryWindow,
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

    private companion object {
        val TARGET_TYPE = AiRunTargetType.NEWS_SUMMARY
        val log = LoggerFactory.getLogger(SummarizeNewsService::class.java)

        fun failureReasonOf(error: Throwable): AiFailureReason {
            return when (error) {
                is LlmProviderException -> failureReasonOf(error.failure)
                is NewsReaderException, is KeywordReaderException -> AiFailureReason.SERVER_ERROR
                // 도메인 불변식 위반은 이 키워드의 입력이나 응답에서 온다.
                is IllegalArgumentException -> AiFailureReason.INVALID_RESPONSE
                else -> AiFailureReason.UNKNOWN
            }
        }

        fun failureReasonOf(failure: LlmFailure): AiFailureReason {
            return when (failure.category) {
                LlmFailureCategory.TIMEOUT -> AiFailureReason.TIMEOUT
                LlmFailureCategory.RATE_LIMITED -> AiFailureReason.RATE_LIMITED
                LlmFailureCategory.TRANSIENT_ERROR -> when (failure.source) {
                    LlmFailureSource.NETWORK -> AiFailureReason.NETWORK_ERROR
                    else -> AiFailureReason.SERVER_ERROR
                }

                LlmFailureCategory.VALIDATION_ERROR,
                LlmFailureCategory.AUTHORIZATION_ERROR -> AiFailureReason.CLIENT_ERROR

                LlmFailureCategory.INVALID_RESPONSE -> AiFailureReason.INVALID_RESPONSE
                LlmFailureCategory.UNKNOWN -> AiFailureReason.UNKNOWN
            }
        }

        /**
         * 키워드에 책임을 물을 수 있는 실패인지 판단한다.
         *
         * 판별할 수 없는 실패는 인프라 쪽으로 본다. 잘못 세면 정상 키워드가 영구 격리되고, 놓치면 다음 실행이 다시 시도한다.
         */
        fun keywordBound(error: Throwable): Boolean {
            return when (error) {
                is LlmProviderException -> error.failure.keywordBound
                is NewsReaderException, is KeywordReaderException -> false
                is IllegalArgumentException -> true
                else -> false
            }
        }

        /**
         * 이번 실행의 남은 키워드까지 막는 전역 장애인지 판단한다.
         */
        fun abortsRun(failure: LlmFailure): Boolean {
            return failure.category == LlmFailureCategory.RATE_LIMITED
        }
    }
}
