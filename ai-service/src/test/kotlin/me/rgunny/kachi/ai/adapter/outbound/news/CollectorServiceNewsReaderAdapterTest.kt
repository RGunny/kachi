package me.rgunny.kachi.ai.adapter.outbound.news

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.NewsReaderErrorCode
import me.rgunny.kachi.ai.application.exception.NewsReaderException
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.jsonExchangeFunction
import me.rgunny.kachi.ai.config.CollectorServiceNewsProperties
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("CollectorServiceNewsReaderAdapter")
class CollectorServiceNewsReaderAdapterTest {

    @Nested
    @DisplayName("findNews()")
    inner class FindNews {

        @Test
        @DisplayName("collector-service 내부 API 응답을 뉴스 요약 입력으로 변환한다")
        fun findNews() = runBlocking {
            val adapter = adapterOf(
                """
                {
                  "success": true,
                  "data": [
                    {
                      "id": "018f0000-0000-7000-8000-000000000001",
                      "source": "GOOGLE",
                      "title": "NVIDIA news",
                      "url": "https://news.example.com/nvidia",
                      "publishedAt": "2026-06-03T00:00:00Z",
                      "collectedAt": "2026-06-03T00:01:00Z",
                      "matchedKeywords": ["NVIDIA"]
                    }
                  ]
                }
                """.trimIndent()
            )

            val articles = adapter.findNews(
                keyword = AiKeyword.of("NVIDIA"),
                from = AiTestFixture.NOW,
                to = AiTestFixture.NOW.plus(Duration.ofDays(1)),
                limit = 20
            )

            assertEquals(1, articles.size)
            assertEquals("GOOGLE", articles.first().source)
            assertEquals("NVIDIA news", articles.first().title)
            assertEquals(listOf("NVIDIA"), articles.first().matchedKeywords)
        }

        @Test
        @DisplayName("collector-service 응답이 실패이면 예외를 던진다")
        fun throwWhenResponseIsFailure() = runBlocking {
            val adapter = adapterOf("""{ "success": false, "data": [] }""")

            val exception = assertFailsWith<NewsReaderException> {
                adapter.findNews(AiKeyword.of("NVIDIA"), null, null, 20)
            }

            assertEquals(NewsReaderErrorCode.COLLECTOR_SERVICE_RESPONSE_FAILED, exception.errorCode)
            assertEquals("NEWS_READER_RESPONSE_FAILED", exception.errorCode.code)
        }

        @Test
        @DisplayName("collector-service 응답 data가 없으면 예외를 던진다")
        fun throwWhenResponseDataIsMissing() = runBlocking {
            val adapter = adapterOf("""{ "success": true }""")

            val exception = assertFailsWith<NewsReaderException> {
                adapter.findNews(AiKeyword.of("NVIDIA"), null, null, 20)
            }

            assertEquals(NewsReaderErrorCode.COLLECTOR_SERVICE_RESPONSE_MISSING_DATA, exception.errorCode)
            assertEquals("NEWS_READER_RESPONSE_MISSING_DATA", exception.errorCode.code)
        }

        @Test
        @DisplayName("collector-service HTTP 오류는 요청 실패 예외로 변환한다")
        fun throwWhenCollectorServiceReturnsErrorStatus() = runBlocking {
            val adapter = adapterOf(
                responseBody = """{ "success": false }""",
                status = HttpStatus.INTERNAL_SERVER_ERROR
            )

            val exception = assertFailsWith<NewsReaderException> {
                adapter.findNews(AiKeyword.of("NVIDIA"), null, null, 20)
            }

            assertEquals(NewsReaderErrorCode.COLLECTOR_SERVICE_REQUEST_FAILED, exception.errorCode)
            assertEquals("NEWS_READER_REQUEST_FAILED", exception.errorCode.code)
        }
    }

    private fun adapterOf(
        responseBody: String,
        status: HttpStatus = HttpStatus.OK
    ): CollectorServiceNewsReaderAdapter {
        return CollectorServiceNewsReaderAdapter(
            webClient = WebClient.builder()
                .exchangeFunction(jsonExchangeFunction(responseBody, status))
                .build(),
            properties = CollectorServiceNewsProperties(
                baseUrl = "http://collector-service",
                newsPath = "/api/v1/internal/news",
                timeout = Duration.ofSeconds(1),
                maxInMemorySize = 256 * 1024,
            )
        )
    }
}
