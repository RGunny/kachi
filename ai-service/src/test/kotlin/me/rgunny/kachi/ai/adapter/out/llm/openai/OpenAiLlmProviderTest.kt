package me.rgunny.kachi.ai.adapter.out.llm.openai

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.ai.config.OpenAiProviderProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

@DisplayName("OpenAiLlmProvider")
class OpenAiLlmProviderTest {

    private val properties = OpenAiProviderProperties(
        enabled = true,
        apiKey = "api-key",
        baseUrl = "https://llm.example.com/v1",
        model = "test-model"
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

    private fun providerOf(exchangeFunction: ExchangeFunction): OpenAiLlmProvider {
        return OpenAiLlmProvider(
            webClient = WebClient.builder()
                .baseUrl(properties.baseUrl)
                .exchangeFunction(exchangeFunction)
                .build(),
            objectMapper = jacksonObjectMapper(),
            providerType = OpenAiProviderType.OPENROUTER,
            properties = properties,
            keywordExpansionPromptVersion = PromptVersion.of("keyword-expansion-v1"),
            newsSummaryPromptVersion = PromptVersion.of("news-summary-v1")
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
