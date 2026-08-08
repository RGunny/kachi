package me.rgunny.kachi.collector.adapter.outbound.client.google

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("GoogleNewsRssProvider")
class GoogleNewsRssProviderTest {

    private val properties = GoogleNewsProperties(
        enabled = true,
        baseUrl = "https://news.google.com",
        rssSearchPath = "/rss/search",
        languageCode = "ko",
        countryCode = "KR",
        connectTimeout = java.time.Duration.ofSeconds(2),
        responseTimeout = java.time.Duration.ofSeconds(5),
        readTimeout = java.time.Duration.ofSeconds(5),
        writeTimeout = java.time.Duration.ofSeconds(5),
        maxInMemorySize = 512 * 1024,
    )

    private val keyword = CollectedKeyword.of("NVIDIA")

    @Nested
    @DisplayName("collect()")
    inner class Collect {

        @Test
        @DisplayName("Google RSS XML을 수집 기사 목록으로 변환한다")
        fun collectGoogleRssItems() = runBlocking {
            val provider = providerOf(
                """
                <rss>
                  <channel>
                    <item>
                      <title>NVIDIA 실적 발표</title>
                      <link>https://kachi.com/news/1</link>
                      <pubDate>Wed, 27 May 2026 10:00:00 GMT</pubDate>
                      <source>Kachi News</source>
                    </item>
                    <item>
                      <title>NVIDIA 신제품 공개</title>
                      <link>https://kachi.com/news/2</link>
                      <pubDate>Wed, 27 May 2026 11:00:00 GMT</pubDate>
                      <source>Kachi News</source>
                    </item>
                  </channel>
                </rss>
                """.trimIndent()
            )

            val articles = provider.collect(keyword)

            assertEquals(2, articles.size)
            assertEquals(NewsSource.GOOGLE, articles[0].source)
            assertEquals("NVIDIA 실적 발표", articles[0].title)
            assertEquals("https://kachi.com/news/1", articles[0].url)
            assertEquals(Instant.parse("2026-05-27T10:00:00Z"), articles[0].publishedAt)
        }

        @Test
        @DisplayName("title 또는 link가 비어 있는 RSS item은 제외한다")
        fun skipItemsWithoutRequiredFields() = runBlocking {
            val provider = providerOf(
                """
                <rss>
                  <channel>
                    <item>
                      <title>NVIDIA 정상 뉴스</title>
                      <link>https://kachi.com/news/1</link>
                    </item>
                    <item>
                      <title></title>
                      <link>https://kachi.com/news/2</link>
                    </item>
                    <item>
                      <title>NVIDIA 링크 없음</title>
                    </item>
                  </channel>
                </rss>
                """.trimIndent()
            )

            val articles = provider.collect(keyword)

            assertEquals(1, articles.size)
            assertEquals("NVIDIA 정상 뉴스", articles.first().title)
        }

        @Test
        @DisplayName("pubDate를 파싱할 수 없으면 publishedAt을 null로 둔다")
        fun useNullPublishedAtWhenPubDateCannotBeParsed() = runBlocking {
            val provider = providerOf(
                """
                <rss>
                  <channel>
                    <item>
                      <title>NVIDIA 날짜 형식 오류</title>
                      <link>https://kachi.com/news/1</link>
                      <pubDate>not-a-date</pubDate>
                    </item>
                  </channel>
                </rss>
                """.trimIndent()
            )

            val articles = provider.collect(keyword)

            assertEquals(1, articles.size)
            assertNull(articles.first().publishedAt)
        }
    }

    private fun providerOf(xml: String): GoogleNewsRssProvider {
        return GoogleNewsRssProvider(
            webClient = webClientReturning(xml),
            properties = properties
        )
    }

    private fun webClientReturning(body: String): WebClient {
        val exchangeFunction = ExchangeFunction {
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .body(body)
                    .build()
            )
        }

        return WebClient.builder()
            .exchangeFunction(exchangeFunction)
            .build()
    }
}
