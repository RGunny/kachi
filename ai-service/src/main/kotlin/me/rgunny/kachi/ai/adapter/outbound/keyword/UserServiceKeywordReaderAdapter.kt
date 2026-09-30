package me.rgunny.kachi.ai.adapter.outbound.keyword

import kotlinx.coroutines.reactor.awaitSingle
import me.rgunny.kachi.ai.application.exception.KeywordReaderErrorCode
import me.rgunny.kachi.ai.application.exception.KeywordReaderException
import me.rgunny.kachi.ai.application.port.outbound.keyword.KeywordReaderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

class UserServiceKeywordReaderAdapter(
    private val webClient: WebClient,
    private val activeKeywordsPath: String,
    private val timeout: Duration
) : KeywordReaderPort {

    override suspend fun findActiveKeywords(): List<AiKeyword> {

        // 1. user-service 에서 사용자들이 등록한 활성 키워드 목록을 조회한다.
        val response = webClient.get()
            .uri(activeKeywordsPath)
            .retrieve()
            .onStatus({ it.isError }) { response ->
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("")
                    .map { body ->
                        KeywordReaderException(
                            errorCode = KeywordReaderErrorCode.USER_SERVICE_REQUEST_FAILED,
                            detail = "status=${response.statusCode().value()}, body=${body.take(MAX_ERROR_BODY_LENGTH)}"
                        )
                    }
            }
            .bodyToMono(ACTIVE_KEYWORDS_RESPONSE_TYPE)
            .timeout(timeout)
            .awaitSingle()

        // 2. API envelope이 실패이거나 data가 없으면 AI 처리 대상 키워드를 확정할 수 없으므로 실패시킨다.
        if (!response.success) {
            throw KeywordReaderException(KeywordReaderErrorCode.USER_SERVICE_RESPONSE_FAILED)
        }
        val keywordResponses = response.data
            ?: throw KeywordReaderException(KeywordReaderErrorCode.USER_SERVICE_RESPONSE_MISSING_DATA)

        // 3. user-service 응답 이름을 ai-service 도메인의 AiKeyword로 변환한다.
        val keywords = keywordResponses
            .map { AiKeyword.of(it.name) }
            .distinct()

        // 4. 활성 키워드가 없으면 AI 실행을 성공 처리하지 않고 설정/데이터 상태를 드러낸다.
        if (keywords.isEmpty()) {
            throw KeywordReaderException(KeywordReaderErrorCode.ACTIVE_KEYWORDS_EMPTY)
        }

        return keywords
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 500

        // Generic 응답 타입을 런타임에도 유지해서 WebClient가 List 내부 타입까지 역직렬화할 수 있게 한다.
        val ACTIVE_KEYWORDS_RESPONSE_TYPE =
            object : ParameterizedTypeReference<UserServiceApiResponse<List<UserServiceActiveKeywordResponse>>>() {}
    }
}
