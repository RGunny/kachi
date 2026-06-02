package me.rgunny.kachi.collector.adapter.`in`.web

import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.adapter.`in`.web.response.ErrorCode
import me.rgunny.kachi.collector.application.port.out.NewsProviderPort
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 외부 뉴스 provider가 실제로 응답하는지 확인하는 internal/admin API다.
 *
 * 저장은 하지 않고 provider 호출과 응답 파싱까지만 확인한다.
 */
@RestController
class NewsProviderHealthController(
    private val newsProviderPorts: List<NewsProviderPort>
) {

    @GetMapping(ApiPaths.INTERNAL_NEWS_PROVIDER_HEALTH, version = ApiVersions.V1)
    suspend fun checkNewsProvider(
        @PathVariable source: String,
        @RequestParam(defaultValue = DEFAULT_KEYWORD) keyword: String
    ): ResponseEntity<ApiResponse<*>> {
        log.info(
            "News provider health check requested: source={}, keyword={}",
            source,
            keyword.toLogValue()
        )

        // 1. path의 source 값을 collector가 지원하는 NewsSource로 변환한다.
        val newsSource = source.toNewsSource() ?: run {
            log.info("News provider health check rejected: source={}, reason=invalid_source", source)
            return ErrorCode.INVALID_NEWS_SOURCE.toResponse()
        }

        // 2. 설정으로 활성화된 provider 구현체를 찾는다.
        val provider = newsProviderPorts.firstOrNull { it.source == newsSource } ?: run {
            log.info("News provider health check rejected: source={}, reason=provider_not_enabled", newsSource)
            return ErrorCode.NEWS_PROVIDER_NOT_ENABLED.toResponse()
        }

        // 3. 실제 외부 provider를 호출해 연결과 응답 파싱을 확인한다.
        val collectedKeyword = CollectedKeyword.of(keyword)
        val articles = provider.collect(collectedKeyword)
        log.info(
            "News provider health check finished: source={}, keyword={}, fetched={}",
            newsSource,
            collectedKeyword.value.toLogValue(),
            articles.size
        )

        return ResponseEntity.ok(
            ApiResponse.success(
                NewsProviderHealthResponse.from(
                    source = newsSource,
                    keyword = collectedKeyword.value,
                    articles = articles
                )
            )
        )
    }

    private fun String.toNewsSource(): NewsSource? {
        return runCatching { NewsSource.valueOf(trim().uppercase()) }.getOrNull()
    }

    private fun ErrorCode.toResponse(): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.status(status).body(ApiResponse.failure(this))
    }

    private companion object {
        const val DEFAULT_KEYWORD = "NVIDIA"
        const val MAX_LOG_KEYWORD_LENGTH = 80

        val log = LoggerFactory.getLogger(NewsProviderHealthController::class.java)

        fun String.toLogValue(): String {
            val normalized = trim()
            return if (normalized.length <= MAX_LOG_KEYWORD_LENGTH) {
                normalized
            } else {
                "${normalized.take(MAX_LOG_KEYWORD_LENGTH)}..."
            }
        }
    }
}
