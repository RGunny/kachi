package me.rgunny.kachi.ai.adapter.out.llm.openai

import io.netty.handler.timeout.ReadTimeoutException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.dto.news.NewsArticle
import me.rgunny.kachi.ai.config.OpenAiProviderProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureCategory
import me.rgunny.kachi.ai.domain.llm.LlmFailureSource
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.CapturingExchangeFunction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("OpenAiLlmProvider")
class OpenAiLlmProviderTest {

    private val properties = OpenAiProviderProperties(
        enabled = true,
        apiKey = "api-key",
        baseUrl = "https://llm.example.com/v1",
        chatCompletionsPath = "/chat/completions",
        model = "test-model",
        connectTimeout = Duration.ofSeconds(2),
        responseTimeout = Duration.ofSeconds(10),
        readTimeout = Duration.ofSeconds(10),
        writeTimeout = Duration.ofSeconds(10),
        maxInMemorySize = 512 * 1024,
    )

    @Test
    @DisplayName("키워드 확장 요청을 chat completions API로 보내고 JSON 배열 응답을 변환한다")
    fun expandKeyword() = runBlocking {
        val exchange = CapturingExchangeFunction(
            """
            {
              "model": "test-model",
              "choices": [
                {
                  "message": {
                    "content": "[\"AI 반도체\", \"GPU\", \"NVIDIA 실적\"]"
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 12,
                "completion_tokens": 8
              }
            }
            """.trimIndent()
        )
        val provider = providerOf(exchange)

        val result = provider.expandKeyword(
            keyword = AiKeyword.of("NVIDIA"),
            maxExpansions = 2
        )

        assertEquals(listOf("AI 반도체", "GPU"), result.expandedKeywords.map { it.value })
        assertEquals("openrouter", result.metadata.provider.value)
        assertEquals("test-model", result.metadata.model.value)
        assertEquals("keyword-expansion-v1", result.metadata.promptVersion.value)
        assertEquals(12, result.metadata.tokenUsage.inputTokens)
        assertEquals(8, result.metadata.tokenUsage.outputTokens)

        val request = exchange.request
        assertEquals("/v1/chat/completions", request.url().path)
        assertEquals("Bearer api-key", request.headers().getFirst("Authorization"))
    }

    @Test
    @DisplayName("JSON 배열이 markdown code fence에 감싸져 있어도 변환한다")
    fun expandKeywordWithFencedJsonArray() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction(
                """
                {
                  "model": "test-model",
                  "choices": [
                    {
                      "message": {
                        "content": "```json\n[\"NVIDIA\", \"GPU\"]\n```"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 12,
                    "completion_tokens": 8
                  }
                }
                """.trimIndent()
            )
        )

        val result = provider.expandKeyword(
            keyword = AiKeyword.of("NVIDIA"),
            maxExpansions = 2
        )

        assertEquals(listOf("NVIDIA", "GPU"), result.expandedKeywords.map { it.value })
    }

    @Test
    @DisplayName("뉴스 요약 요청을 chat completions API로 보내고 JSON 객체 응답을 변환한다")
    fun summarizeNews() = runBlocking {
        val exchange = CapturingExchangeFunction(
            """
            {
              "model": "test-model",
              "choices": [
                {
                  "message": {
                    "content": "{\"title\":\"NVIDIA 실적 기대\",\"content\":\"NVIDIA 관련 뉴스가 AI 수요를 중심으로 전개됐다. 데이터센터와 GPU 수요가 핵심 변수로 언급됐다.\",\"sentiment\":\"POSITIVE\"}"
                  }
                }
              ],
              "usage": {
                "prompt_tokens": 30,
                "completion_tokens": 15
              }
            }
            """.trimIndent()
        )
        val provider = providerOf(exchange)

        val result = provider.summarizeNews(
            keyword = AiKeyword.of("NVIDIA"),
            articles = listOf(newsArticle())
        )

        assertEquals("NVIDIA 실적 기대", result.title)
        assertEquals(NewsSummarySentiment.POSITIVE, result.sentiment)
        assertEquals("openrouter", result.metadata.provider.value)
        assertEquals("test-model", result.metadata.model.value)
        assertEquals("news-summary-v1", result.metadata.promptVersion.value)
        assertEquals(30, result.metadata.tokenUsage.inputTokens)
        assertEquals(15, result.metadata.tokenUsage.outputTokens)
    }

    @Test
    @DisplayName("뉴스 요약 JSON 객체가 markdown code fence에 감싸져 있어도 변환한다")
    fun summarizeNewsWithFencedJsonObject() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction(
                """
                {
                  "model": "test-model",
                  "choices": [
                    {
                      "message": {
                        "content": "```json\n{\"title\":\"요약\",\"content\":\"본문\",\"sentiment\":\"NEUTRAL\"}\n```"
                      }
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5
                  }
                }
                """.trimIndent()
            )
        )

        val result = provider.summarizeNews(
            keyword = AiKeyword.of("NVIDIA"),
            articles = listOf(newsArticle())
        )

        assertEquals("요약", result.title)
        assertEquals("본문", result.content)
        assertEquals(NewsSummarySentiment.NEUTRAL, result.sentiment)
    }

    @Test
    @DisplayName("뉴스 요약 JSON 객체에 추가 필드가 있어도 필요한 필드만 변환한다")
    fun summarizeNewsWithUnknownJsonFields() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction(
                """
                {
                  "model": "test-model",
                  "choices": [
                    {
                      "message": {
                        "content": "{\"title\":\"요약\",\"content\":\"본문\",\"sentiment\":\"NEUTRAL\",\"reason\":\"extra\"}"
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val result = provider.summarizeNews(
            keyword = AiKeyword.of("NVIDIA"),
            articles = listOf(newsArticle())
        )

        assertEquals("요약", result.title)
        assertEquals("본문", result.content)
        assertEquals(NewsSummarySentiment.NEUTRAL, result.sentiment)
    }

    @Test
    @DisplayName("뉴스 요약 응답에 JSON 객체가 없으면 INVALID_RESPONSE로 분류한다")
    fun classifyMissingJsonObjectAsInvalidResponse() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction(
                """
                {
                  "model": "test-model",
                  "choices": [
                    {
                      "message": {
                        "content": "요약 결과입니다."
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(
                keyword = AiKeyword.of("NVIDIA"),
                articles = listOf(newsArticle())
            )
        }

        assertEquals(LlmFailureCategory.INVALID_RESPONSE, exception.failure.category)
        // provider가 살아 있다는 응답이므로 재시도 대상이 아니고, 이 키워드에 책임을 물을 수 있다.
        assertFalse(exception.failure.retryable)
        assertTrue(exception.failure.keywordBound)
        assertEquals("openrouter", exception.failure.provider.value)
    }

    @Test
    @DisplayName("응답 content가 비어 있으면 INVALID_RESPONSE로 분류한다")
    fun classifyEmptyContentAsInvalidResponse() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction("""{"model":"test-model","choices":[{"message":{"content":"   "}}]}""")
        )

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.INVALID_RESPONSE, exception.failure.category)
    }

    @Test
    @DisplayName("429 응답을 RATE_LIMITED로 분류하고 Retry-After를 보존한다")
    fun classifyTooManyRequestsAsRateLimited() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds = 30))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.RATE_LIMITED, exception.failure.category)
        assertEquals(429, exception.failure.statusCode)
        assertEquals(30_000L, exception.failure.retryAfterMillis)
        assertTrue(exception.failure.retryable)
        assertFalse(exception.failure.keywordBound)
    }

    @Test
    @DisplayName("401 응답을 AUTHORIZATION_ERROR로 분류해 키워드에 책임을 묻지 않는다")
    fun classifyUnauthorizedAsAuthorizationError() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.UNAUTHORIZED))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.AUTHORIZATION_ERROR, exception.failure.category)
        assertEquals(401, exception.failure.statusCode)
        assertFalse(exception.failure.keywordBound)
        assertFalse(exception.failure.retryable)
    }

    @Test
    @DisplayName("그 외 4xx 응답을 VALIDATION_ERROR로 분류한다")
    fun classifyClientErrorAsValidationError() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.BAD_REQUEST))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.VALIDATION_ERROR, exception.failure.category)
        assertTrue(exception.failure.keywordBound)
        assertFalse(exception.failure.retryable)
    }

    @Test
    @DisplayName("5xx 응답을 TRANSIENT_ERROR로 분류한다")
    fun classifyServerErrorAsTransientError() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.SERVICE_UNAVAILABLE))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.TRANSIENT_ERROR, exception.failure.category)
        assertEquals(503, exception.failure.statusCode)
        assertTrue(exception.failure.retryable)
        assertFalse(exception.failure.keywordBound)
    }

    @Test
    @DisplayName("timeout 예외가 cause 체인에 있으면 TIMEOUT으로 분류한다")
    fun classifyTimeoutFromCauseChain() = runBlocking {
        val provider = providerOf(
            ExchangeFunction {
                Mono.error(
                    WebClientRequestException(
                        IllegalStateException("wrapped", ReadTimeoutException.INSTANCE),
                        HttpMethod.POST,
                        URI.create("https://llm.example.com/v1/chat/completions"),
                        HttpHeaders.EMPTY
                    )
                )
            }
        )

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.TIMEOUT, exception.failure.category)
        assertEquals(LlmFailureSource.NETWORK, exception.failure.source)
        assertTrue(exception.failure.retryable)
    }

    @Test
    @DisplayName("timeout이 아닌 연결 실패는 NETWORK 원천의 TRANSIENT_ERROR로 분류한다")
    fun classifyConnectionFailureAsNetworkTransientError() = runBlocking {
        val provider = providerOf(
            ExchangeFunction {
                Mono.error(
                    WebClientRequestException(
                        java.net.ConnectException("connection refused"),
                        HttpMethod.POST,
                        URI.create("https://llm.example.com/v1/chat/completions"),
                        HttpHeaders.EMPTY
                    )
                )
            }
        )

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCategory.TRANSIENT_ERROR, exception.failure.category)
        assertEquals(LlmFailureSource.NETWORK, exception.failure.source)
    }

    @Test
    @DisplayName("요약 대상 뉴스가 없으면 실패한다")
    fun failWhenNewsArticlesAreEmpty() = runBlocking {
        val provider = providerOf(CapturingExchangeFunction("""{"model":"test-model"}"""))

        assertFailsWith<IllegalArgumentException> {
            provider.summarizeNews(
                keyword = AiKeyword.of("NVIDIA"),
                articles = emptyList()
            )
        }
        Unit
    }

    private fun providerOf(exchangeFunction: ExchangeFunction): OpenAiLlmProvider {
        return OpenAiLlmProvider(
            webClient = WebClient.builder()
                .baseUrl(properties.baseUrl)
                .exchangeFunction(exchangeFunction)
                .build(),
            jsonMapper = JsonMapper.builder().build(),
            providerType = OpenAiProviderType.OPENROUTER,
            properties = properties,
            keywordExpansionPromptVersion = PromptVersion.of("keyword-expansion-v1"),
            newsSummaryPromptVersion = PromptVersion.of("news-summary-v1")
        )
    }

    private fun newsArticle(): NewsArticle {
        return AiTestFixture.newsArticle(title = "NVIDIA AI GPU demand rises")
    }

    private fun errorExchangeFunction(
        status: HttpStatus,
        retryAfterSeconds: Long? = null
    ): ExchangeFunction {
        return ExchangeFunction {
            val response = ClientResponse.create(status)
                .header("Content-Type", "application/json")
                .body("""{"error":{"message":"provider rejected the request"}}""")

            retryAfterSeconds?.let { response.header(HttpHeaders.RETRY_AFTER, it.toString()) }

            Mono.just(response.build())
        }
    }

}
