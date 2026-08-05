package me.rgunny.kachi.ai.adapter.out.llm.openai

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.config.OpenAiProviderProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.summary.NewsSummarySentiment
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
    @DisplayName("뉴스 요약 응답에 JSON 객체가 없으면 실패한다")
    fun failWhenNewsSummaryResponseDoesNotContainJsonObject() = runBlocking {
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

        assertFailsWith<IllegalArgumentException> {
            provider.summarizeNews(
                keyword = AiKeyword.of("NVIDIA"),
                articles = listOf(newsArticle())
            )
        }
        Unit
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
