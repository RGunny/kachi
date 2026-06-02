package me.rgunny.kachi.collector.adapter.out.client.finnhub

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.collector.application.port.out.CollectedArticle
import me.rgunny.kachi.collector.application.port.out.NewsProviderPort
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.springframework.web.reactive.function.client.WebClient
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class FinnhubNewsProvider(
    private val webClient: WebClient,
    private val properties: FinnhubNewsProperties,
    private val clock: Clock
) : NewsProviderPort {

    override val source: NewsSource = NewsSource.FINNHUB

    override suspend fun collect(keyword: CollectedKeyword): List<CollectedArticle> {
        val to = LocalDate.now(clock)
        val from = to.minusDays(properties.lookbackDays)
        val symbol = keyword.value.trim().uppercase()

        // 1. Finnhub company-news endpoint를 JSON으로 호출한다.
        val response = webClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path(properties.companyNewsPath)
                    .queryParam("symbol", symbol)
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .build()
            }
            .header(FINNHUB_TOKEN_HEADER, properties.apiKey)
            .retrieve()
            .bodyToMono(Array<FinnhubNewsItem>::class.java)
            .awaitSingle()

        // 2. headline과 URL이 없는 item은 adapter 안에서 제외한다.
        return response.mapNotNull { toCollectedArticleOrNull(it) }
    }

    private fun toCollectedArticleOrNull(item: FinnhubNewsItem): CollectedArticle? {
        val title = item.headline.orEmpty().trim()
        val url = item.url.orEmpty().trim()

        if (title.isBlank() || url.isBlank()) {
            return null
        }

        return CollectedArticle(
            source = NewsSource.FINNHUB,
            title = title,
            url = url,
            publishedAt = parsePublishedAt(item.datetime)
        )
    }

    /**
     * Finnhub datetime은 UNIX timestamp 초 단위다. 값이 없거나 epoch 이전이면 해당 기사 시각만 null로 둔다.
     */
    private fun parsePublishedAt(datetime: Long?): Instant? {
        if (datetime == null || datetime < 0) return null

        return Instant.ofEpochSecond(datetime)
    }

    private companion object {
        private const val FINNHUB_TOKEN_HEADER = "X-Finnhub-Token"
    }
}
