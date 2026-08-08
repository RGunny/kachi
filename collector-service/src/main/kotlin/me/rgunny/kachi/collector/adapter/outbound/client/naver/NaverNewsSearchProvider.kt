package me.rgunny.kachi.collector.adapter.outbound.client.naver

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.collector.application.port.outbound.news.model.CollectedArticle
import me.rgunny.kachi.collector.application.port.outbound.news.NewsProviderPort
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.HtmlUtils
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NaverNewsSearchProvider(
    private val webClient: WebClient,
    private val properties: NaverNewsProperties
) : NewsProviderPort {

    override val source: NewsSource = NewsSource.NAVER

    override suspend fun collect(keyword: CollectedKeyword): List<CollectedArticle> {
        // 1. Naver Search API 뉴스 검색 endpoint를 JSON으로 호출한다.
        val response = webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(properties.newsSearchPath)
                    .queryParam("query", keyword.value)
                    .queryParam("display", properties.display)
                    .queryParam("start", properties.start)
                    .queryParam("sort", properties.sort)
                    .build()
            }
            .header(NAVER_CLIENT_ID_HEADER, properties.clientId)
            .header(NAVER_CLIENT_SECRET_HEADER, properties.clientSecret)
            .retrieve()
            .bodyToMono(NaverNewsSearchResponse::class.java)
            .awaitSingle()

        // 2. title과 URL이 없는 item은 adapter 안에서 제외한다.
        return response.items.orEmpty()
            .mapNotNull { toCollectedArticleOrNull(it) }
    }

    private fun toCollectedArticleOrNull(item: NaverNewsItem): CollectedArticle? {
        val title = sanitize(item.title.orEmpty())
        // 원문 URL(originallink)을 우선 저장하고, 없으면 Naver 뉴스 URL(link)을 사용한다.
        val url = item.originallink.orEmpty().ifBlank { item.link.orEmpty() }.trim()

        if (title.isBlank() || url.isBlank()) {
            return null
        }

        return CollectedArticle(
            source = NewsSource.NAVER,
            title = title,
            url = url,
            publishedAt = parsePublishedAt(item.pubDate.orEmpty())
        )
    }

    private fun sanitize(value: String): String {
        return HtmlUtils.htmlUnescape(value)
            .replace(HTML_TAG_REGEX, "")
            .trim()
    }

    /**
     * Naver Search API pubDate를 Instant로 변환한다. 파싱할 수 없으면 해당 기사 시각만 null로 둔다.
     */
    private fun parsePublishedAt(pubDate: String): Instant? {
        if (pubDate.isBlank()) return null

        return runCatching {
            ZonedDateTime.parse(pubDate, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull()
    }

    private companion object {
        private const val NAVER_CLIENT_ID_HEADER = "X-Naver-Client-Id"
        private const val NAVER_CLIENT_SECRET_HEADER = "X-Naver-Client-Secret"
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
    }
}
