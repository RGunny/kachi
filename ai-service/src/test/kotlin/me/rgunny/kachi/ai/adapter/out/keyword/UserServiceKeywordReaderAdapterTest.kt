package me.rgunny.kachi.ai.adapter.out.keyword

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.KeywordReaderErrorCode
import me.rgunny.kachi.ai.application.exception.KeywordReaderException
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("UserServiceKeywordReaderAdapter")
class UserServiceKeywordReaderAdapterTest {

    @Nested
    @DisplayName("findActiveKeywords()")
    inner class FindActiveKeywords {

        @Test
        @DisplayName("user-service 내부 API 응답을 AI 키워드로 변환한다")
        fun findActiveKeywords() = runBlocking {
            val adapter = adapterOf(
                """
                {
                  "success": true,
                  "data": [
                    { "name": "NVIDIA" },
                    { "name": "Tesla" },
                    { "name": "NVIDIA" }
                  ]
                }
                """.trimIndent()
            )

            val keywords = adapter.findActiveKeywords()

            assertEquals(
                listOf(
                    AiKeyword.of("NVIDIA"),
                    AiKeyword.of("Tesla")
                ),
                keywords
            )
        }

        @Test
        @DisplayName("user-service 응답이 실패이면 예외를 던진다")
        fun throwWhenResponseIsFailure() = runBlocking {
            val adapter = adapterOf("""{ "success": false, "data": [] }""")

            val exception = assertFailsWith<KeywordReaderException> {
                adapter.findActiveKeywords()
            }

            assertEquals(KeywordReaderErrorCode.USER_SERVICE_RESPONSE_FAILED, exception.errorCode)
            assertEquals("KEYWORD_READER_002", exception.errorCode.code)
        }

        @Test
        @DisplayName("user-service 응답 data가 없으면 예외를 던진다")
        fun throwWhenResponseDataIsMissing() = runBlocking {
            val adapter = adapterOf("""{ "success": true }""")

            val exception = assertFailsWith<KeywordReaderException> {
                adapter.findActiveKeywords()
            }

            assertEquals(KeywordReaderErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA, exception.errorCode)
            assertEquals("KEYWORD_READER_003", exception.errorCode.code)
        }

        @Test
        @DisplayName("활성 키워드가 비어 있으면 예외를 던진다")
        fun throwWhenActiveKeywordsAreEmpty() = runBlocking {
            val adapter = adapterOf("""{ "success": true, "data": [] }""")

            val exception = assertFailsWith<KeywordReaderException> {
                adapter.findActiveKeywords()
            }

            assertEquals(KeywordReaderErrorCode.ACTIVE_KEYWORDS_EMPTY, exception.errorCode)
            assertEquals("KEYWORD_READER_004", exception.errorCode.code)
        }

        @Test
        @DisplayName("user-service HTTP 오류는 요청 실패 예외로 변환한다")
        fun throwWhenUserServiceReturnsErrorStatus() = runBlocking {
            val adapter = adapterOf(
                responseBody = """{ "success": false }""",
                status = HttpStatus.INTERNAL_SERVER_ERROR
            )

            val exception = assertFailsWith<KeywordReaderException> {
                adapter.findActiveKeywords()
            }

            assertEquals(KeywordReaderErrorCode.USER_SERVICE_REQUEST_FAILED, exception.errorCode)
            assertEquals("KEYWORD_READER_001", exception.errorCode.code)
        }
    }

    private fun adapterOf(
        responseBody: String,
        status: HttpStatus = HttpStatus.OK
    ): UserServiceKeywordReaderAdapter {
        return UserServiceKeywordReaderAdapter(
            webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction(responseBody, status))
                .build(),
            properties = UserServiceKeywordProperties(
                baseUrl = "http://user-service",
                timeout = Duration.ofSeconds(1)
            )
        )
    }

    private fun exchangeFunction(responseBody: String, status: HttpStatus): ExchangeFunction {
        return ExchangeFunction {
            Mono.just(
                ClientResponse.create(status)
                    .header("Content-Type", "application/json")
                    .body(responseBody)
                    .build()
            )
        }
    }
}
