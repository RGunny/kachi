package me.rgunny.kachi.ai.adapter.out.llm.openai

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.port.out.llm.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.out.llm.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.out.llm.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.out.llm.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.out.news.NewsArticle
import me.rgunny.kachi.ai.config.OpenAiProviderProperties
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmModelName
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import org.springframework.web.reactive.function.client.WebClient

/**
 * OpenAI 계열 chat completions API를 사용하는 LLM provider adapter.
 *
 * OpenRouter, Groq, Together, Cerebras, Mistral처럼 같은 `/chat/completions`
 * 계약을 제공하는 provider를 하나의 adapter로 연결한다.
 */
class OpenAiLlmProvider(
    private val webClient: WebClient,
    private val objectMapper: ObjectMapper,
    private val providerType: OpenAiProviderType,
    private val properties: OpenAiProviderProperties,
    private val keywordExpansionPromptVersion: PromptVersion,
    private val newsSummaryPromptVersion: PromptVersion
) : LlmProviderPort {

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        val preparedPlan = LlmNewsSummaryPlan(
            provider = LlmProviderName.of(providerType.value),
            model = LlmModelName.of(properties.model),
            promptVersion = newsSummaryPromptVersion
        )

        return object : PreparedLlmNewsSummary {
            override val plan: LlmNewsSummaryPlan = preparedPlan

            override suspend fun summarize(
                keyword: AiKeyword,
                articles: List<NewsArticle>
            ): LlmNewsSummaryResult {
                return this@OpenAiLlmProvider.summarizeNews(keyword, articles)
            }
        }
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        // 1. 키워드 확장 전용 prompt를 chat completions API로 보낸다.
        val response = requestChatCompletion(
            systemPrompt = KEYWORD_EXPANSION_SYSTEM_PROMPT,
            userPrompt = """
                원본 키워드: ${keyword.value}
                최대 개수: $maxExpansions
            """.trimIndent(),
            maxTokens = 256
        )

        // 2. provider 응답의 첫 번째 message content를 JSON 문자열 배열로 해석한다.
        val content = response.firstContent()
        val expandedKeywords = parseJsonStringArray(content)
            .map(ExpandedKeyword::of)
            .take(maxExpansions)

        require(expandedKeywords.isNotEmpty()) { "LLM keyword expansion response is empty" }

        return LlmKeywordExpansionResult(
            expandedKeywords = expandedKeywords,
            // 3. 결과가 어떤 provider/model/prompt에서 나왔는지 application 계층으로 전달한다.
            metadata = metadata(response, keywordExpansionPromptVersion)
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        throw UnsupportedOperationException("news summary is not implemented yet")
    }

    private suspend fun requestChatCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): OpenAiChatResponse {
        return webClient.post()
            .uri(properties.chatCompletionsPath)
            .header(AUTHORIZATION_HEADER, "Bearer ${properties.apiKey}")
            .bodyValue(
                OpenAiChatRequest(
                    model = properties.model,
                    messages = listOf(
                        OpenAiChatMessage(role = "system", content = systemPrompt),
                        OpenAiChatMessage(role = "user", content = userPrompt)
                    ),
                    max_tokens = maxTokens
                )
            )
            .retrieve()
            .bodyToMono(OpenAiChatResponse::class.java)
            .awaitSingle()
    }

    private fun OpenAiChatResponse.firstContent(): String {
        return choices.firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: error("LLM response content is empty")
    }

    private fun parseJsonStringArray(content: String): List<String> {
        val jsonArray = extractJsonArray(content)

        return objectMapper.readValue(jsonArray, STRING_LIST_TYPE)
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    /**
     * 일부 provider는 JSON 배열만 요청해도 markdown code fence를 덧붙인다.
     * 파싱 안정성을 위해 전체 content에서 첫 `[`부터 마지막 `]`까지만 JSON 배열로 사용한다.
     */
    private fun extractJsonArray(content: String): String {
        val startIndex = content.indexOf('[')
        val endIndex = content.lastIndexOf(']')

        require(startIndex >= 0 && endIndex > startIndex) {
            "LLM keyword expansion response must contain a JSON string array"
        }

        return content.substring(startIndex, endIndex + 1)
    }

    private fun metadata(
        response: OpenAiChatResponse,
        promptVersion: PromptVersion
    ): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = LlmProviderName.of(providerType.value),
            model = LlmModelName.of(response.model?.takeIf { it.isNotBlank() } ?: properties.model),
            promptVersion = promptVersion,
            tokenUsage = TokenUsage(
                inputTokens = response.usage?.prompt_tokens ?: 0,
                outputTokens = response.usage?.completion_tokens ?: 0
            )
        )
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val KEYWORD_EXPANSION_SYSTEM_PROMPT =
            "너는 뉴스 검색 키워드 확장기다. 응답은 한국어 또는 영어 키워드 문자열 JSON 배열만 반환한다."
        val STRING_LIST_TYPE = object : TypeReference<List<String>>() {}
    }
}
