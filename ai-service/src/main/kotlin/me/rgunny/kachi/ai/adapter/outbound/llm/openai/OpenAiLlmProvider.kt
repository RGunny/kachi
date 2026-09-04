package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.adapter.outbound.llm.LlmHttpExceptionClassifier
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.PromptVersion
import me.rgunny.kachi.ai.domain.llm.ReasoningEffort
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

/**
 * OpenAI chat completions 규격으로 모델 하나를 부르는 adapter.
 *
 * [LlmProviderPort]를 전략 패턴으로 구현한 것 중 [me.rgunny.kachi.ai.domain.llm.LlmApi.OPENAI_CHAT_COMPLETIONS] 규격 담당이다.
 * 요청 body를 만들고, 옵션을 이 규격의 철자로 싣고, 응답에서 content와 모델 이름을 꺼내고, HTTP status를 실패 코드로 옮기는
 * 것까지가 이 전략의 책임이다. 다른 규격은 다른 adapter가 같은 포트로 구현하므로 가드와 라우터는 어느 쪽이든 같은 방식으로 다룬다.
 *
 * 이 규격을 내는 제공자는 여럿이지만 요청·응답의 모양은 하나라 adapter도 하나다.
 * 어느 제공자의 어느 모델을 부르는지는 [model]이 정하고, 주소와 timeout은 [webClient]에 이미 들어 있다.
 *
 * 호출 실패는 모두 [LlmProviderException]으로 변환해 원천과 성격을 application 계층에 전달한다.
 * 여기서 재시도하지 않는다. 재시도 구동은 scheduler tick이 맡는다(ADR 021).
 */
class OpenAiLlmProvider(
    private val webClient: WebClient,
    private val jsonMapper: JsonMapper,
    private val model: LlmModel,
    private val apiKey: String,
    private val keywordExpansionPromptVersion: PromptVersion,
    private val newsSummaryPromptVersion: PromptVersion
) : LlmProviderPort {

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return OpenAiPreparedNewsSummary(
            plan = LlmNewsSummaryPlan(
                provider = model.provider,
                promptVersion = newsSummaryPromptVersion
            ),
            provider = this
        )
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

        if (expandedKeywords.isEmpty()) {
            throw invalidResponse("LLM keyword expansion response is empty")
        }

        return LlmKeywordExpansionResult(
            expandedKeywords = expandedKeywords,
            // 3. 결과가 어떤 제공자/모델/prompt에서 나왔는지 application 계층으로 전달한다.
            metadata = metadata(response, keywordExpansionPromptVersion)
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        require(articles.isNotEmpty()) { "news summary articles are required" }

        val response = requestChatCompletion(
            systemPrompt = NEWS_SUMMARY_SYSTEM_PROMPT,
            userPrompt = newsSummaryUserPrompt(keyword, articles),
            maxTokens = NEWS_SUMMARY_MAX_TOKENS
        )
        val parsed = parseNewsSummary(response.firstContent())

        return LlmNewsSummaryResult(
            title = parsed.title.trim(),
            content = parsed.content.trim(),
            sentiment = parsed.sentiment(),
            metadata = metadata(response, newsSummaryPromptVersion)
        )
    }

    private suspend fun requestChatCompletion(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): OpenAiChatResponse {
        return try {
            webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                // 인증이 없는 제공자(self-hosted)에는 헤더를 싣지 않는다.
                .headers { headers -> if (apiKey.isNotBlank()) headers.setBearerAuth(apiKey) }
                .bodyValue(
                    OpenAiChatRequest(
                        model = model.code,
                        messages = listOf(
                            OpenAiChatMessage(role = "system", content = systemPrompt),
                            OpenAiChatMessage(role = "user", content = userPrompt)
                        ),
                        max_tokens = maxTokens,
                        reasoning_effort = reasoningEffort(model.options.reasoningEffort)
                    )
                )
                // status와 Retry-After를 함께 봐야 rate limit을 분류할 수 있어 retrieve() 대신 exchangeToMono를 쓴다.
                .exchangeToMono { response ->
                    if (response.statusCode().isError) {
                        errorResponse(response)
                    } else {
                        response.bodyToMono<OpenAiChatResponse>()
                    }
                }
                .awaitSingle()
        } catch (exception: CancellationException) {
            // coroutine 취소는 provider 장애가 아니므로 실패로 변환하지 않는다.
            throw exception
        } catch (exception: LlmProviderException) {
            throw exception
        } catch (exception: Exception) {
            throw transportException(exception)
        }
    }

    /**
     * 오류 응답의 status와 header를 body보다 먼저 읽어 분류에 사용한다.
     */
    private fun errorResponse(response: ClientResponse): Mono<OpenAiChatResponse> {
        val statusCode = response.statusCode().value()
        val retryAfterMillis = retryAfterMillis(response.headers().asHttpHeaders())

        return response.bodyToMono<String>()
            .defaultIfEmpty("")
            .flatMap { body ->
                Mono.error<OpenAiChatResponse>(httpException(statusCode, retryAfterMillis, body))
            }
    }

    private fun httpException(
        statusCode: Int,
        retryAfterMillis: Long?,
        body: String
    ): LlmProviderException {
        val detail = "status=$statusCode, body=${body.take(MAX_ERROR_BODY_LENGTH)}"
        val code = when {
            statusCode == HTTP_TOO_MANY_REQUESTS -> LlmFailureCode.LLM_RATE_LIMITED
            // 인증 실패는 4xx지만 키워드가 아니라 credential 문제이므로 따로 분류한다.
            statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> LlmFailureCode.LLM_AUTHORIZATION_ERROR
            statusCode in HTTP_CLIENT_ERROR_RANGE -> LlmFailureCode.LLM_CLIENT_ERROR
            else -> LlmFailureCode.LLM_TRANSIENT_ERROR
        }

        return LlmProviderException(
            failure(
                code = code,
                message = "${code.defaultMessage}. $detail",
                statusCode = statusCode,
                retryAfterMillis = retryAfterMillis.takeIf { code == LlmFailureCode.LLM_RATE_LIMITED }
            )
        )
    }

    /**
     * 응답을 받지 못했거나 읽지 못한 실패를 분류한다.
     *
     * timeout은 예외 체인 안쪽에 숨어 있어 타입만 보고는 판별할 수 없다.
     */
    private fun transportException(exception: Exception): LlmProviderException {
        val code = when {
            LlmHttpExceptionClassifier.isTimeout(exception) -> LlmFailureCode.LLM_TIMEOUT
            exception is WebClientRequestException -> LlmFailureCode.LLM_NETWORK_ERROR
            // 응답 body를 읽지 못한 것은 provider가 계약을 어긴 것이므로 재시도 대상으로 보지 않는다.
            else -> LlmFailureCode.LLM_INVALID_RESPONSE
        }

        return LlmProviderException(
            failure = failure(
                code = code,
                message = exception.message?.takeIf { it.isNotBlank() } ?: code.defaultMessage
            ),
            cause = exception
        )
    }

    /**
     * 이 규격의 `reasoning_effort` 철자.
     * 값 이름을 소문자로 싣고, [ReasoningEffort.OMIT]은 필드를 빼서 모델 기본값에 맡긴다.
     */
    private fun reasoningEffort(effort: ReasoningEffort): String? {
        return when (effort) {
            ReasoningEffort.OMIT -> null
            ReasoningEffort.NONE,
            ReasoningEffort.LOW,
            ReasoningEffort.MEDIUM,
            ReasoningEffort.HIGH,
            ReasoningEffort.MAX -> effort.name.lowercase()
        }
    }

    private fun retryAfterMillis(headers: HttpHeaders): Long? {
        return headers.getFirst(HttpHeaders.RETRY_AFTER)
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.let { it * MILLIS_PER_SECOND }
    }

    private fun failure(
        code: LlmFailureCode,
        message: String = code.defaultMessage,
        statusCode: Int? = null,
        retryAfterMillis: Long? = null
    ): LlmFailure {
        return LlmFailure(
            code = code,
            provider = model.provider,
            message = message,
            statusCode = statusCode,
            retryAfterMillis = retryAfterMillis
        )
    }

    private fun invalidResponse(message: String): LlmProviderException {
        return LlmProviderException(failure(code = LlmFailureCode.LLM_INVALID_RESPONSE, message = message))
    }

    private fun OpenAiChatResponse.firstContent(): String {
        return choices.firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: throw invalidResponse("LLM response content is empty")
    }

    private fun parseJsonStringArray(content: String): List<String> {
        val jsonArray = extractJsonArray(content)
        val parsed = runCatching { jsonMapper.readValue(jsonArray, STRING_LIST_TYPE) }
            .getOrElse { throw invalidResponse("LLM keyword expansion response is not a JSON string array") }

        return parsed
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

        if (startIndex !in 0..<endIndex) {
            throw invalidResponse("LLM keyword expansion response must contain a JSON string array")
        }

        return content.substring(startIndex, endIndex + 1)
    }

    private fun newsSummaryUserPrompt(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): String {
        val articleLines = articles.mapIndexed { index, article ->
            """
            ${index + 1}. id=${article.id}
               source=${article.source}
               title=${article.title}
               url=${article.url}
               publishedAt=${article.publishedAt?.toString().orEmpty()}
            """.trimIndent()
        }.joinToString(separator = "\n")

        return """
            키워드: ${keyword.value}

            뉴스 목록:
            $articleLines

            응답 JSON 형식:
            {
              "title": "요약 제목",
              "content": "3~5문장 요약 본문",
              "sentiment": "POSITIVE|NEUTRAL|NEGATIVE|UNKNOWN"
            }
        """.trimIndent()
    }

    private fun parseNewsSummary(content: String): ParsedNewsSummary {
        val jsonObject = extractJsonObject(content)
        val parsed = runCatching {
            jsonMapper
                .readerFor(ParsedNewsSummary::class.java)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue<ParsedNewsSummary>(jsonObject)
        }.getOrElse { throw invalidResponse("LLM news summary response is not a valid JSON object") }

        if (parsed.title.isBlank()) {
            throw invalidResponse("LLM news summary title is empty")
        }
        if (parsed.content.isBlank()) {
            throw invalidResponse("LLM news summary content is empty")
        }

        return parsed
    }

    /**
     * 일부 provider는 JSON 객체만 요청해도 markdown code fence나 설명 문장을 덧붙인다.
     * 파싱 안정성을 위해 전체 content에서 첫 `{`부터 마지막 `}`까지만 JSON 객체로 사용한다.
     */
    private fun extractJsonObject(content: String): String {
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')

        if (startIndex !in 0..<endIndex) {
            throw invalidResponse("LLM news summary response must contain a JSON object")
        }

        return content.substring(startIndex, endIndex + 1)
    }

    private fun metadata(
        response: OpenAiChatResponse,
        promptVersion: PromptVersion
    ): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = model.provider,
            // 응답이 보고한 모델을 우선한다. 요청한 code와 다를 수 있고, 그 사실이 기록에 남아야 한다.
            model = response.model?.takeIf { it.isNotBlank() } ?: model.code,
            promptVersion = promptVersion,
            tokenUsage = TokenUsage(
                inputTokens = response.usage?.prompt_tokens ?: 0,
                outputTokens = response.usage?.completion_tokens ?: 0
            )
        )
    }

    companion object {
        /** 이 규격의 생성 endpoint. 제공자마다 다르지 않으므로 설정이 아니라 상수다. */
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        /** 응답 body를 메모리에 받는 상한. 요약 응답은 수 KB라 넉넉하다. */
        const val MAX_IN_MEMORY_SIZE = 512 * 1024

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val HTTP_CLIENT_ERROR_RANGE = 400..499
        private const val MAX_ERROR_BODY_LENGTH = 500
        private const val MILLIS_PER_SECOND = 1_000L
        private const val KEYWORD_EXPANSION_SYSTEM_PROMPT =
            "너는 뉴스 검색 키워드 확장기다. 응답은 한국어 또는 영어 키워드 문자열 JSON 배열만 반환한다."
        private const val NEWS_SUMMARY_SYSTEM_PROMPT =
            "너는 뉴스 요약기다. 응답은 title, content, sentiment 필드를 가진 JSON 객체만 반환한다."
        private const val NEWS_SUMMARY_MAX_TOKENS = 768
        private val STRING_LIST_TYPE = object : TypeReference<List<String>>() {}
    }
}
