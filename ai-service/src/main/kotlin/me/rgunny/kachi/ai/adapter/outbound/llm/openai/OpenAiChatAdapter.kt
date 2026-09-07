package me.rgunny.kachi.ai.adapter.outbound.llm.openai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.adapter.outbound.llm.LlmHttpExceptionClassifier
import me.rgunny.kachi.ai.application.exception.LlmProviderException
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderPort
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmGenerationMetadata
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmKeywordExpansionResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryPlan
import me.rgunny.kachi.ai.application.port.outbound.llm.model.LlmNewsSummaryResult
import me.rgunny.kachi.ai.application.port.outbound.llm.model.PreparedLlmNewsSummary
import me.rgunny.kachi.ai.application.port.outbound.news.model.NewsArticle
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.keyword.ExpandedKeyword
import me.rgunny.kachi.ai.domain.llm.KeywordExpansionPrompt
import me.rgunny.kachi.ai.domain.llm.LlmFailure
import me.rgunny.kachi.ai.domain.llm.LlmFailureCode
import me.rgunny.kachi.ai.domain.llm.LlmModel
import me.rgunny.kachi.ai.domain.llm.LlmPrompt
import me.rgunny.kachi.ai.domain.llm.NewsSummaryPrompt
import me.rgunny.kachi.ai.domain.llm.TokenUsage
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper

/**
 * OpenAI chat completions 규격으로 모델 하나를 부르는 adapter.
 *
 * [LlmProviderPort]를 전략 패턴으로 구현한 것 중 [me.rgunny.kachi.ai.domain.llm.LlmApi.OPENAI_CHAT_COMPLETIONS] 규격 담당이다.
 * 프롬프트를 이 규격의 메시지로 옮기고, 옵션을 이 규격의 철자로 싣고, 응답에서 content와 모델 이름을 꺼내고,
 * HTTP status를 실패 코드로 옮기는 것까지가 이 전략의 책임이다.
 * 다른 규격은 다른 adapter가 같은 포트로 구현하므로 가드와 라우터는 어느 쪽이든 같은 방식으로 다룬다.
 *
 * 이 규격을 내는 제공자는 여럿이지만 요청·응답의 모양은 하나라 adapter도 하나다.
 * 어느 제공자의 어느 모델을 부르는지는 [model]이 정하고, 주소·인증·timeout은 [webClient]에 이미 들어 있다.
 *
 * JSON 모드(`response_format`)는 항상 켠다. 두 용도의 응답이 모두 JSON이고, 켜 두면 code fence나 설명 문장이 덧붙는 일이 준다.
 *
 * 호출 실패는 모두 HTTP status를 실패 코드로 옮긴 [LlmProviderException]으로 변환해 application 계층에 전달한다.
 * 여기서 재시도하지 않는다. 재시도 구동은 scheduler tick이 맡는다(ADR 021).
 */
class OpenAiChatAdapter(
    private val webClient: WebClient,
    private val jsonMapper: JsonMapper,
    private val model: LlmModel
) : LlmProviderPort {

    override fun prepareNewsSummary(): PreparedLlmNewsSummary {
        return OpenAiPreparedNewsSummary(
            plan = LlmNewsSummaryPlan(
                provider = model.provider,
                promptVersion = NewsSummaryPrompt.version
            ),
            adapter = this
        )
    }

    override suspend fun expandKeyword(
        keyword: AiKeyword,
        maxExpansions: Int
    ): LlmKeywordExpansionResult {
        val response = requestChatCompletion(
            prompt = KeywordExpansionPrompt,
            input = KeywordExpansionPrompt.input(keyword, maxExpansions)
        )

        val expandedKeywords = parseKeywordExpansion(response.firstContent())
            .map(ExpandedKeyword::of)
            .take(maxExpansions)

        if (expandedKeywords.isEmpty()) {
            throw invalidResponse("LLM keyword expansion response is empty")
        }

        return LlmKeywordExpansionResult(
            expandedKeywords = expandedKeywords,
            metadata = metadata(response, KeywordExpansionPrompt)
        )
    }

    override suspend fun summarizeNews(
        keyword: AiKeyword,
        articles: List<NewsArticle>
    ): LlmNewsSummaryResult {
        require(articles.isNotEmpty()) { "news summary articles are required" }

        val response = requestChatCompletion(
            prompt = NewsSummaryPrompt,
            input = NewsSummaryPrompt.input(keyword, articles.map(::promptArticle))
        )
        val parsed = parseNewsSummary(response.firstContent())

        return LlmNewsSummaryResult(
            title = parsed.title.trim(),
            content = parsed.content.trim(),
            sentiment = parsed.sentiment(),
            metadata = metadata(response, NewsSummaryPrompt)
        )
    }

    /**
     * [input]은 프롬프트가 만든 입력 객체다. JSON으로 직렬화해 user 메시지로 싣는다.
     * 자연어 템플릿에 값을 끼워 넣지 않으므로 기사 제목 속 줄바꿈·따옴표·지시문이 메시지 구조를 바꾸지 못한다.
     */
    private suspend fun requestChatCompletion(
        prompt: LlmPrompt,
        input: Any
    ): OpenAiChatResponse {
        val options = OpenAiRequestOptions.from(model.options)
        val userPrompt = jsonMapper.writeValueAsString(input)

        return try {
            webClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .bodyValue(
                    OpenAiChatRequest(
                        model = model.code,
                        messages = listOf(
                            OpenAiChatMessage(role = "system", content = prompt.system),
                            OpenAiChatMessage(role = "user", content = userPrompt)
                        ),
                        max_tokens = prompt.maxTokens,
                        response_format = OpenAiResponseFormat.JSON_OBJECT,
                        reasoning_effort = options.reasoningEffort,
                        thinking = options.thinking
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
        // 요청 본문(400·413·422), 모델(404), 계정(401·402·403)
        val code = when (statusCode) {
            HTTP_BAD_REQUEST, HTTP_PAYLOAD_TOO_LARGE, HTTP_UNPROCESSABLE_CONTENT -> LlmFailureCode.LLM_REQUEST_REJECTED
            HTTP_UNAUTHORIZED -> LlmFailureCode.LLM_UNAUTHORIZED
            HTTP_PAYMENT_REQUIRED -> LlmFailureCode.LLM_PAYMENT_REQUIRED
            HTTP_FORBIDDEN -> LlmFailureCode.LLM_FORBIDDEN
            HTTP_NOT_FOUND -> LlmFailureCode.LLM_MODEL_NOT_FOUND
            HTTP_TOO_MANY_REQUESTS -> LlmFailureCode.LLM_RATE_LIMITED
            in HTTP_SERVER_ERROR_RANGE -> LlmFailureCode.LLM_SERVER_ERROR
            else -> LlmFailureCode.LLM_UNKNOWN_ERROR
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

    private fun parseKeywordExpansion(content: String): List<String> {
        val jsonObject = extractJsonObject(content, "LLM keyword expansion response must contain a JSON object")
        val parsed = runCatching {
            jsonMapper
                .readerFor(ParsedKeywordExpansion::class.java)
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue<ParsedKeywordExpansion>(jsonObject)
        }.getOrElse { throw invalidResponse("LLM keyword expansion response is not a valid JSON object") }

        return parsed.keywords
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun promptArticle(article: NewsArticle): NewsSummaryPrompt.Article {
        return NewsSummaryPrompt.Article(
            source = article.source,
            title = article.title,
            publishedAt = article.publishedAt
        )
    }

    private fun parseNewsSummary(content: String): ParsedNewsSummary {
        val jsonObject = extractJsonObject(content, "LLM news summary response must contain a JSON object")
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
     * 일부 provider는 JSON 모드에서도 markdown code fence나 설명 문장을 덧붙인다.
     * 파싱 안정성을 위해 전체 content에서 첫 `{`부터 마지막 `}`까지만 JSON 객체로 사용한다.
     */
    private fun extractJsonObject(
        content: String,
        missingMessage: String
    ): String {
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')

        if (startIndex !in 0..<endIndex) {
            throw invalidResponse(missingMessage)
        }

        return content.substring(startIndex, endIndex + 1)
    }

    private fun metadata(
        response: OpenAiChatResponse,
        prompt: LlmPrompt
    ): LlmGenerationMetadata {
        return LlmGenerationMetadata(
            provider = model.provider,
            requestedModel = model.code,
            // 응답이 보고한 모델을 우선한다. 요청한 code와 다를 수 있고, 그 사실이 기록에 남아야 한다.
            model = response.model?.takeIf { it.isNotBlank() } ?: model.code,
            promptVersion = prompt.version,
            tokenUsage = TokenUsage(
                inputTokens = response.usage?.prompt_tokens ?: 0,
                outputTokens = response.usage?.completion_tokens ?: 0
            )
        )
    }

    companion object {
        /** 이 규격의 생성 endpoint. 제공자마다 다르지 않으므로 설정이 아니라 상수다. */
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_PAYMENT_REQUIRED = 402
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private const val HTTP_UNPROCESSABLE_CONTENT = 422
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERROR_RANGE = 500..599
        private const val MAX_ERROR_BODY_LENGTH = 500
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
