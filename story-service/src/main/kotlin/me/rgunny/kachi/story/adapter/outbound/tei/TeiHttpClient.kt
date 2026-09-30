package me.rgunny.kachi.story.adapter.outbound.tei

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiEmbedRequest
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiInfoResponse
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiRerankRequest
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiRerankScore
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiToken
import me.rgunny.kachi.story.adapter.outbound.tei.dto.TeiTokenizeRequest
import me.rgunny.kachi.story.application.exception.InferenceException
import me.rgunny.kachi.story.domain.inference.InferenceFailure
import me.rgunny.kachi.story.domain.inference.InferenceFailureCode
import me.rgunny.kachi.story.domain.inference.InferenceTarget
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

/**
 * 추론 서버 하나를 HTTP로 부르는 [TeiClient] 구현.
 *
 * [expectedDimension]은 임베딩 서버에만 있다.
 * 재시도하지 않는다.
 */
class TeiHttpClient(
    private val webClient: WebClient,
    private val target: InferenceTarget,
    private val expectedDimension: Int? = null
) : TeiClient {

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        require(texts.isNotEmpty()) { "임베딩 입력은 하나 이상이어야 합니다" }

        val vectors = exchange(EMBED_PATH, TeiEmbedRequest(inputs = texts), EMBED_RESPONSE)

        if (vectors.size != texts.size) {
            throw invalidResponse("embedding count mismatch: expected=${texts.size}, actual=${vectors.size}")
        }
        vectors.forEachIndexed { index, vector ->
            if (expectedDimension != null && vector.size != expectedDimension) {
                throw invalidResponse("embedding dimension mismatch at $index: expected=$expectedDimension, actual=${vector.size}")
            }
            if (vector.any { !it.isFinite() }) {
                throw invalidResponse("embedding at $index contains a non-finite value")
            }
        }

        return vectors
    }

    override suspend fun rerank(query: String, texts: List<String>): List<Double> {
        require(texts.isNotEmpty()) { "판정 후보는 하나 이상이어야 합니다" }

        val ranked = exchange(RERANK_PATH, TeiRerankRequest(query = query, texts = texts), RERANK_RESPONSE)

        if (ranked.size != texts.size) {
            throw invalidResponse("rerank count mismatch: expected=${texts.size}, actual=${ranked.size}")
        }
        // 점수순으로 오는 행을 index로 입력 순서에 되돌린다.
        val scores = arrayOfNulls<Double>(texts.size)
        ranked.forEach { row ->
            val index = row.index ?: throw invalidResponse("rerank row without index")
            val score = row.score ?: throw invalidResponse("rerank row without score at $index")
            if (index !in texts.indices) {
                throw invalidResponse("rerank index out of range: $index")
            }
            if (scores[index] != null) {
                throw invalidResponse("rerank index duplicated: $index")
            }
            if (!score.isFinite() || score !in 0.0..1.0) {
                throw invalidResponse("rerank score out of sigmoid range at $index: $score")
            }
            scores[index] = score
        }

        return scores.map { it ?: throw invalidResponse("rerank index missing") }
    }

    override suspend fun info(): TeiInfoResponse {
        return call { webClient.get().uri(INFO_PATH).exchangeToMono { response -> body(response, INFO_RESPONSE) } }
    }

    /** 입력마다 토큰 수를 돌려준다. */
    suspend fun countTokens(texts: List<String>): List<Int> {
        require(texts.isNotEmpty()) { "토큰화 입력은 하나 이상이어야 합니다" }

        val tokenized = exchange(TOKENIZE_PATH, TeiTokenizeRequest(inputs = texts), TOKENIZE_RESPONSE)

        if (tokenized.size != texts.size) {
            throw invalidResponse("tokenize count mismatch: expected=${texts.size}, actual=${tokenized.size}")
        }

        return tokenized.map { it.size }
    }

    private suspend fun <T : Any> exchange(
        path: String,
        request: Any,
        responseType: ParameterizedTypeReference<T>
    ): T {
        return call {
            webClient.post()
                .uri(path)
                .bodyValue(request)
                .exchangeToMono { response -> body(response, responseType) }
        }
    }

    private suspend fun <T : Any> call(request: () -> Mono<T>): T {
        return try {
            request().awaitSingle()
        } catch (exception: CancellationException) {
            // coroutine 취소는 실패로 변환하지 않는다.
            throw exception
        } catch (exception: InferenceException) {
            throw exception
        } catch (exception: Exception) {
            throw transportException(exception)
        }
    }

    /** status를 body보다 먼저 읽어 분류하고 본문 앞부분을 detail로 남긴다. */
    private fun <T : Any> body(
        response: ClientResponse,
        responseType: ParameterizedTypeReference<T>
    ): Mono<T> {
        if (!response.statusCode().isError) {
            return response.bodyToMono(responseType)
        }
        val statusCode = response.statusCode().value()

        return response.bodyToMono<String>()
            .defaultIfEmpty("")
            .flatMap { body -> Mono.error(httpException(statusCode, body)) }
    }

    private fun httpException(
        statusCode: Int,
        body: String
    ): InferenceException {
        // 추론 서버의 상태 표: Empty 400, body limit 413, Validation·Tokenizer 422, Backend 424, Overloaded 429, Unhealthy 503
        val code = when (statusCode) {
            HTTP_BAD_REQUEST -> InferenceFailureCode.INFERENCE_INPUT_EMPTY
            HTTP_PAYLOAD_TOO_LARGE -> InferenceFailureCode.INFERENCE_PAYLOAD_TOO_LARGE
            HTTP_UNPROCESSABLE_CONTENT -> InferenceFailureCode.INFERENCE_INPUT_INVALID
            HTTP_FAILED_DEPENDENCY -> InferenceFailureCode.INFERENCE_FAILED
            HTTP_TOO_MANY_REQUESTS -> InferenceFailureCode.INFERENCE_OVERLOADED
            HTTP_SERVICE_UNAVAILABLE -> InferenceFailureCode.INFERENCE_UNHEALTHY
            in HTTP_SERVER_ERROR_RANGE -> InferenceFailureCode.INFERENCE_SERVER_ERROR
            else -> InferenceFailureCode.INFERENCE_UNKNOWN_ERROR
        }

        return InferenceException(
            failure(
                code = code,
                message = "${code.defaultMessage}. status=$statusCode, body=${body.take(MAX_ERROR_BODY_LENGTH)}",
                statusCode = statusCode
            )
        )
    }

    /** 응답을 받지 못했거나 읽지 못한 실패를 분류한다. */
    private fun transportException(exception: Exception): InferenceException {
        val code = when {
            TeiHttpExceptionClassifier.isTimeout(exception) -> InferenceFailureCode.INFERENCE_TIMEOUT
            exception is WebClientRequestException -> InferenceFailureCode.INFERENCE_NETWORK_ERROR
            // 응답 body를 읽지 못한 것은 계약 위반이다.
            else -> InferenceFailureCode.INFERENCE_INVALID_RESPONSE
        }

        return InferenceException(
            failure = failure(
                code = code,
                message = exception.message?.takeIf { it.isNotBlank() } ?: code.defaultMessage
            ),
            cause = exception
        )
    }

    private fun failure(
        code: InferenceFailureCode,
        message: String = code.defaultMessage,
        statusCode: Int? = null
    ): InferenceFailure {
        return InferenceFailure(
            code = code,
            target = target,
            message = message,
            statusCode = statusCode
        )
    }

    private fun invalidResponse(message: String): InferenceException {
        return InferenceException(failure(code = InferenceFailureCode.INFERENCE_INVALID_RESPONSE, message = message))
    }

    companion object {
        const val EMBED_PATH = "/embed"
        const val RERANK_PATH = "/rerank"
        const val INFO_PATH = "/info"
        const val TOKENIZE_PATH = "/tokenize"

        private val EMBED_RESPONSE = object : ParameterizedTypeReference<List<FloatArray>>() {}
        private val RERANK_RESPONSE = object : ParameterizedTypeReference<List<TeiRerankScore>>() {}
        private val INFO_RESPONSE = object : ParameterizedTypeReference<TeiInfoResponse>() {}
        private val TOKENIZE_RESPONSE = object : ParameterizedTypeReference<List<List<TeiToken>>>() {}

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private const val HTTP_UNPROCESSABLE_CONTENT = 422
        private const val HTTP_FAILED_DEPENDENCY = 424
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private val HTTP_SERVER_ERROR_RANGE = 500..599
        private const val MAX_ERROR_BODY_LENGTH = 500
    }
}
