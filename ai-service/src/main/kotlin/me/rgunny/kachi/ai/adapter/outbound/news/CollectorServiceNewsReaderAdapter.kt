package me.rgunny.kachi.ai.adapter.outbound.news

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.exception.NewsReaderErrorCode
import me.rgunny.kachi.ai.application.exception.NewsReaderException
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.application.port.outbound.news.NewsReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.Optional

@Component
class CollectorServiceNewsReaderAdapter(
    @param:Qualifier("collectorServiceWebClient")
    private val webClient: WebClient,
    private val properties: CollectorServiceNewsProperties
) : NewsReaderPort {

    override suspend fun findNews(
        keyword: AiKeyword,
        from: Instant?,
        to: Instant?,
        limit: Int
    ): List<NewsArticle> {

        // 1. collector-service 에서 키워드와 기간에 해당하는 저장 뉴스를 조회한다.
        val response = webClient.get()
            .uri { builder ->
                builder.path(properties.newsPath)
                    .queryParam("keyword", keyword.value)
                    .queryParamIfPresent("from", optionalInstant(from))
                    .queryParamIfPresent("to", optionalInstant(to))
                    .queryParam("limit", limit)
                    .build()
            }
            .retrieve()
            .onStatus({ it.isError }) { response ->
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("")
                    .map { body ->
                        NewsReaderException(
                            errorCode = NewsReaderErrorCode.COLLECTOR_SERVICE_REQUEST_FAILED,
                            detail = "status=${response.statusCode().value()}, body=${body.take(MAX_ERROR_BODY_LENGTH)}"
                        )
                    }
            }
            .bodyToMono(NEWS_RESPONSE_TYPE)
            .timeout(properties.timeout)
            .awaitSingle()

        // 2. API envelope이 실패이거나 data가 없으면 요약 대상 뉴스를 확정할 수 없으므로 실패시킨다.
        if (!response.success) {
            throw NewsReaderException(NewsReaderErrorCode.COLLECTOR_SERVICE_RESPONSE_FAILED)
        }
        val newsResponses = response.data
            ?: throw NewsReaderException(NewsReaderErrorCode.COLLECTOR_SERVICE_RESPONSE_MISSING_DATA)

        // 3. collector-service 응답을 LLM 요약 입력 전용 NewsArticle로 변환한다.
        return newsResponses.map {
            NewsArticle(
                id = it.id,
                source = it.source,
                title = it.title,
                url = it.url,
                publishedAt = it.publishedAt,
                collectedAt = it.collectedAt,
                matchedKeywords = it.matchedKeywords
            )
        }
    }

    private fun optionalInstant(value: Instant?): Optional<String> {
        return value?.let { Optional.of(it.toString()) } ?: Optional.empty()
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 500

        // Generic 응답 타입을 런타임에도 유지해서 WebClient가 List 내부 타입까지 역직렬화할 수 있게 한다.
        val NEWS_RESPONSE_TYPE =
            object : ParameterizedTypeReference<CollectorServiceApiResponse<List<CollectorServiceNewsResponse>>>() {}
    }
}
