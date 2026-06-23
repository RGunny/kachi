package me.rgunny.kachi.collector.adapter.out.client.naver

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("NaverNewsSearchProvider")
class NaverNewsSearchProviderTest {

    private val properties = NaverNewsProperties(
        enabled = true,
        baseUrl = "https://openapi.naver.com",
        newsSearchPath = "/v1/search/news.json",
        clientId = "client-id",
        clientSecret = "client-secret",
        display = 100,
        start = 1,
        sort = "date",
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
        @DisplayName("Naver 뉴스 검색 JSON을 수집 기사 목록으로 변환한다")
        fun collectNaverNewsItems() = runBlocking {
            val exchange = CapturingExchangeFunction(
                """
                {
                  "total": 2,
                  "start": 1,
                  "display": 2,
                  "items": [
                    {
                      "title": "<b>NVIDIA</b> 실적 발표",
                      "originallink": "https://kachi.com/news/1",
                      "link": "https://n.news.naver.com/1",
                      "description": "NVIDIA 뉴스",
                      "pubDate": "Wed, 27 May 2026 10:00:00 +0900"
                    },
                    {
                      "title": "TESLA 신제품 공개",
                      "originallink": "",
                      "link": "https://n.news.naver.com/2",
                      "description": "TESLA 뉴스",
                      "pubDate": "Wed, 27 May 2026 11:00:00 +0900"
                    }
                  ]
                }
                """.trimIndent()
            )
            val provider = providerOf(exchange)

            val articles = provider.collect(keyword)

            assertEquals(2, articles.size)
            assertEquals(NewsSource.NAVER, articles[0].source)
            assertEquals("NVIDIA 실적 발표", articles[0].title)
            assertEquals("https://kachi.com/news/1", articles[0].url)
            assertEquals(Instant.parse("2026-05-27T01:00:00Z"), articles[0].publishedAt)
            assertEquals("https://n.news.naver.com/2", articles[1].url)

            val request = exchange.request
            assertEquals("client-id", request.headers().getFirst("X-Naver-Client-Id"))
            assertEquals("client-secret", request.headers().getFirst("X-Naver-Client-Secret"))
            assertEquals("/v1/search/news.json", request.url().path)
            assertEquals("query=NVIDIA&display=100&start=1&sort=date", request.url().query)
        }

        @Test
        @DisplayName("title 또는 URL이 비어 있는 item은 제외한다")
        fun skipItemsWithoutRequiredFields() = runBlocking {
            val provider = providerOf(
                CapturingExchangeFunction(
                    """
                    {
                      "items": [
                        {
                          "title": "NVIDIA 정상 뉴스",
                          "originallink": "https://kachi.com/news/1",
                          "link": "",
                          "pubDate": ""
                        },
                        {
                          "title": "",
                          "originallink": "https://kachi.com/news/2",
                          "link": "",
                          "pubDate": ""
                        },
                        {
                          "title": "NVIDIA URL 없음",
                          "originallink": "",
                          "link": "",
                          "pubDate": ""
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

            val articles = provider.collect(keyword)

            assertEquals(1, articles.size)
            assertEquals("NVIDIA 정상 뉴스", articles.first().title)
        }

        @Test
        @DisplayName("pubDate를 파싱할 수 없으면 publishedAt을 null로 둔다")
        fun useNullPublishedAtWhenPubDateCannotBeParsed() = runBlocking {
            val provider = providerOf(
                CapturingExchangeFunction(
                    """
                    {
                      "items": [
                        {
                          "title": "NVIDIA 날짜 형식 오류",
                          "originallink": "https://kachi.com/news/1",
                          "link": "",
                          "pubDate": "not-a-date"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )

            val articles = provider.collect(keyword)

            assertEquals(1, articles.size)
            assertNull(articles.first().publishedAt)
        }
    }

    private fun providerOf(exchangeFunction: ExchangeFunction): NaverNewsSearchProvider {
        return NaverNewsSearchProvider(
            webClient = WebClient.builder()
                .baseUrl(properties.baseUrl)
                .exchangeFunction(exchangeFunction)
                .build(),
            properties = properties
        )
    }

    private class CapturingExchangeFunction(
        private val body: String
    ) : ExchangeFunction {

        lateinit var request: ClientRequest

        override fun exchange(request: ClientRequest): Mono<ClientResponse> {
            this.request = request

            return Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build()
            )
        }
    }
}
