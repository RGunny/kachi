package me.rgunny.kachi.collector.application.service

import me.rgunny.kachi.collector.application.port.`in`.CollectNewsCommand
import me.rgunny.kachi.collector.application.port.`in`.CollectNewsUseCase
import me.rgunny.kachi.collector.application.port.`in`.CollectionRunResult
import me.rgunny.kachi.collector.application.port.out.CollectedArticle
import me.rgunny.kachi.collector.application.port.out.CollectionRunPersistencePort
import me.rgunny.kachi.collector.application.port.out.KeywordReaderPort
import me.rgunny.kachi.collector.application.port.out.NewsPersistencePort
import me.rgunny.kachi.collector.application.port.out.NewsProviderPort
import me.rgunny.kachi.collector.application.port.out.SaveNewsResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.CollectionRun
import me.rgunny.kachi.collector.domain.CollectionTargetType
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import me.rgunny.kachi.collector.domain.ProviderFailureReason
import me.rgunny.kachi.collector.domain.ProviderCollectionResult
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class CollectNewsService(
    private val keywordReaderPort: KeywordReaderPort,
    private val newsProviderPorts: List<NewsProviderPort>,
    private val newsPersistencePort: NewsPersistencePort,
    private val collectionRunPersistencePort: CollectionRunPersistencePort,
    private val clock: Clock
) : CollectNewsUseCase {

    override suspend fun collect(command: CollectNewsCommand): CollectionRunResult {

        // 1. 수집 대상 키워드와 provider를 확정한다.
        val keywords = command.keywords.ifEmpty {
            keywordReaderPort.findActiveKeywords()
        }
        val providers = newsProviderPorts.filter { provider ->
            command.sources.isEmpty() || provider.source in command.sources
        }

        // 2. 수집 실행을 RUNNING 상태로 먼저 저장한다.
        val startedRun = collectionRunPersistencePort.save(
            CollectionRun.start(
                targetType = CollectionTargetType.NEWS,
                requestedKeywords = keywords.size,
                startedAt = Instant.now(clock)
            )
        )

        // 3. provider별 수집 결과를 만든다. 일부 provider 실패는 전체 예외로 올리지 않는다.
        val providerResults = providers.map { provider ->
            runCatching {
                collectWithProvider(provider, keywords, Instant.now(clock))
            }.getOrElse { error ->
                ProviderCollectionResult.failure(
                    source = provider.source,
                    failureReason = ProviderFailureReason.UNKNOWN,
                    failureMessage = error.toFailureMessage()
                )
            }
        }

        // 4. provider 결과를 기준으로 CollectionRun을 성공/부분 실패/실패로 완료한다.
        val completedRun = collectionRunPersistencePort.save(
            startedRun.complete(
                providerResults = providerResults,
                finishedAt = Instant.now(clock)
            )
        )

        return CollectionRunResult.from(completedRun)
    }

    private suspend fun collectWithProvider(
        provider: NewsProviderPort,
        keywords: List<CollectedKeyword>,
        collectedAt: Instant
    ): ProviderCollectionResult {
        
        // 1. 하나의 provider를 모든 키워드로 조회한다.
        val collectedArticles = keywords.flatMap { keyword ->
            provider.collect(keyword).map { article ->
                CollectedArticleWithKeyword(article = article, keyword = keyword)
            }
        }

        // 2. provider 응답을 News 도메인으로 변환하고, 같은 URL은 키워드를 합쳐 하나로 만든다.
        val candidates = collectedArticles.map { it.toNews(collectedAt) }
        val mergedCandidates = mergeCandidatesByUrl(candidates)
        val batchDuplicateCount = collectedArticles.size - mergedCandidates.size

        // 3. 이미 저장된 URL은 저장 대상에서 제외한다.
        val existingUrlHashes = newsPersistencePort.findExistingUrlHashes(
            source = provider.source,
            mergedCandidates.map { it.urlHash }.toSet()
        )
        val newCandidates = mergedCandidates.filter { it.urlHash !in existingUrlHashes }
        var savedCount = 0
        var duplicateCount = batchDuplicateCount + (mergedCandidates.size - newCandidates.size)

        // 4. 저장 중 unique 충돌이 나면 실패가 아니라 중복으로 집계한다.
        for (news in newCandidates) {
            when (newsPersistencePort.save(news)) {
                SaveNewsResult.SAVED -> savedCount += 1
                SaveNewsResult.DUPLICATED -> duplicateCount += 1
            }
        }

        return ProviderCollectionResult.success(
            source = provider.source,
            fetchedCount = collectedArticles.size,
            savedCount = savedCount,
            duplicateCount = duplicateCount
        )
    }

    private data class CollectedArticleWithKeyword(
        val article: CollectedArticle,
        val keyword: CollectedKeyword
    ) {
        fun toNews(collectedAt: Instant): News {
            return News.create(
                source = article.source,
                title = NewsTitle.of(article.title),
                url = NewsUrl.of(article.url),
                publishedAt = article.publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = listOf(keyword)
            )
        }
    }

    // 저장 전 후보만 병합한다. News.create()로 새 id가 생기므로 저장된 News 병합에는 사용하지 않는다.
    private fun mergeCandidatesByUrl(candidates: List<News>): List<News> {
        return candidates.groupBy { it.source to it.urlHash }
            .values
            .map { sameUrlNews ->
                val first = sameUrlNews.first()
                News.create(
                    source = first.source,
                    title = first.title,
                    url = first.url,
                    publishedAt = first.publishedAt,
                    collectedAt = first.collectedAt,
                    matchedKeywords = sameUrlNews.flatMap { it.matchedKeywords }.distinct()
                )
            }
    }

    private fun Throwable.toFailureMessage(): String {
        return message
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: this::class.simpleName
            ?: UNKNOWN_FAILURE_MESSAGE
    }

    private companion object {
        const val UNKNOWN_FAILURE_MESSAGE = "unknown provider failure"
    }
}
