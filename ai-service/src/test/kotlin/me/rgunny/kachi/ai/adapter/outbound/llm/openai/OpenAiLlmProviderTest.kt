package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import io.netty.handler.timeout.ReadTimeoutException
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailureAttribution
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmProvider
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import me.rgunny.kachi.ai.support.CapturingExchangeFunction
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("OpenAiLlmProvider")
class OpenAiLlmProviderTest {

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
        assertEquals(LlmProvider.GROQ, result.metadata.provider)
        assertEquals("test-model", result.metadata.model)
        assertEquals("keyword-expansion-v1", result.metadata.promptVersion.value)
        assertEquals(12, result.metadata.tokenUsage.inputTokens)
        assertEquals(8, result.metadata.tokenUsage.outputTokens)

        val request = exchange.request
        assertEquals("/v1/chat/completions", request.url().path)
        assertEquals("Bearer api-key", request.headers().getFirst("Authorization"))
        assertEquals(MODEL.code, exchange.sentBody.path("model").asText())
    }

    @Test
    @DisplayName("응답이 모델을 보고하지 않으면 요청한 모델 code를 기록한다")
    fun fallBackToRequestedModelWhenResponseOmitsModel() = runBlocking {
        val provider = providerOf(
            CapturingExchangeFunction("""{"choices":[{"message":{"content":"[\"GPU\"]"}}]}""")
        )

        val result = provider.expandKeyword(keyword = AiKeyword.of("NVIDIA"), maxExpansions = 2)

        assertEquals(MODEL.code, result.metadata.model)
    }

    @Test
    @DisplayName("reasoning effort가 OMIT이면 요청에 싣지 않고, 값이 있으면 소문자 이름으로 싣는다")
    fun serializeReasoningEffortPerModel() = runBlocking {
        val omitted = CapturingExchangeFunction(keywordResponse())
        providerOf(omitted, model = LlmModel.GROQ_QWEN3_27B).expandKeyword(AiKeyword.of("NVIDIA"), 1)
        val none = CapturingExchangeFunction(keywordResponse())
        providerOf(none, model = LlmModel.OLLAMA_QWEN3_27B).expandKeyword(AiKeyword.of("NVIDIA"), 1)

        assertTrue(omitted.sentBody.path("reasoning_effort").isMissingNode)
        assertEquals("none", none.sentBody.path("reasoning_effort").asText())
    }

    @Test
    @DisplayName("api key가 없으면 Authorization 헤더를 싣지 않는다")
    fun omitAuthorizationHeaderWithoutApiKey() = runBlocking {
        val exchange = CapturingExchangeFunction(keywordResponse())

        providerOf(exchange, apiKey = "").expandKeyword(AiKeyword.of("NVIDIA"), 1)

        assertNull(exchange.request.headers().getFirst("Authorization"))
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
        assertEquals(LlmProvider.GROQ, result.metadata.provider)
        assertEquals("test-model", result.metadata.model)
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

        assertEquals(LlmFailureCode.LLM_INVALID_RESPONSE, exception.failure.code)
        // 모델이 살아 있다는 응답이므로 이 키워드의 입력 탓이고 일시 실패가 아니다.
        assertEquals(LlmFailureAttribution.INPUT, exception.failure.attribution)
        assertFalse(exception.failure.transient)
        assertEquals(LlmProvider.GROQ, exception.failure.provider)
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

        assertEquals(LlmFailureCode.LLM_INVALID_RESPONSE, exception.failure.code)
    }

    @Test
    @DisplayName("429 응답을 RATE_LIMITED로 분류하고 Retry-After를 보존한다")
    fun classifyTooManyRequestsAsRateLimited() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.TOO_MANY_REQUESTS, retryAfterSeconds = 30))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(LlmFailureCode.LLM_RATE_LIMITED, exception.failure.code)
        assertEquals(429, exception.failure.statusCode)
        assertEquals(30_000L, exception.failure.retryAfterMillis)
    }

    @ParameterizedTest
    @CsvSource(
        "400, LLM_REQUEST_REJECTED",
        "413, LLM_REQUEST_REJECTED",
        "422, LLM_REQUEST_REJECTED",
        "401, LLM_UNAUTHORIZED",
        "402, LLM_PAYMENT_REQUIRED",
        "403, LLM_FORBIDDEN",
        "404, LLM_MODEL_NOT_FOUND",
        "429, LLM_RATE_LIMITED",
        "500, LLM_SERVER_ERROR",
        "503, LLM_SERVER_ERROR",
        "409, LLM_UNKNOWN_ERROR"
    )
    @DisplayName("HTTP status를 실패 코드로 옮기고 status를 보존한다")
    fun classifyHttpStatus(status: Int, code: LlmFailureCode) = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.valueOf(status)))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertEquals(code, exception.failure.code)
        assertEquals(status, exception.failure.statusCode)
        assertEquals(LlmProvider.GROQ, exception.failure.provider)
    }

    @Test
    @DisplayName("Retry-After는 429에만 보존한다")
    fun keepRetryAfterOnlyForRateLimit() = runBlocking {
        val provider = providerOf(errorExchangeFunction(HttpStatus.SERVICE_UNAVAILABLE, retryAfterSeconds = 30))

        val exception = assertFailsWith<LlmProviderException> {
            provider.summarizeNews(keyword = AiKeyword.of("NVIDIA"), articles = listOf(newsArticle()))
        }

        assertNull(exception.failure.retryAfterMillis)
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

        assertEquals(LlmFailureCode.LLM_TIMEOUT, exception.failure.code)
        assertTrue(exception.failure.transient)
    }

    @Test
    @DisplayName("timeout이 아닌 연결 실패는 NETWORK_ERROR로 분류한다")
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

        assertEquals(LlmFailureCode.LLM_NETWORK_ERROR, exception.failure.code)
        assertTrue(exception.failure.transient)
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

    @Test
    @DisplayName("요약 plan의 제공자는 모델의 제공자다")
    fun planProviderIsModelProvider() {
        val provider = providerOf(ExchangeFunction { Mono.empty() })

        assertEquals(LlmProvider.GROQ, provider.prepareNewsSummary().plan.provider)
    }

    private fun providerOf(
        exchangeFunction: ExchangeFunction,
        model: LlmModel = MODEL,
        apiKey: String = "api-key"
    ): OpenAiLlmProvider {
        return OpenAiLlmProvider(
            webClient = WebClient.builder()
                .baseUrl("https://llm.example.com/v1")
                .exchangeFunction(exchangeFunction)
                .build(),
            jsonMapper = JsonMapper.builder().build(),
            model = model,
            apiKey = apiKey,
            keywordExpansionPromptVersion = PromptVersion.of("keyword-expansion-v1"),
            newsSummaryPromptVersion = PromptVersion.of("news-summary-v1")
        )
    }

    private fun keywordResponse(): String = """{"model":"test-model","choices":[{"message":{"content":"[\"GPU\"]"}}]}"""

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

    private companion object {
        val MODEL: LlmModel = LlmModel.GROQ_QWEN3_27B
    }
}
