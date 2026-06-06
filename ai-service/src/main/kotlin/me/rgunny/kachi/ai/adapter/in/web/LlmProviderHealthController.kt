package me.rgunny.kachi.ai.adapter.`in`.web

import me.rgunny.kachi.ai.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.`in`.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.out.llm.LlmProviderPort
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 LLM provider 연결 확인 API.
 *
 * 현재 LLM provider mode에 따라 실제 키워드 확장 요청을 보내고 응답 파싱까지 확인한다.
 */
@RestController
class LlmProviderHealthController(
    private val llmProviderPort: LlmProviderPort
) {

    @GetMapping(ApiPaths.INTERNAL_LLM_PROVIDER_HEALTH, version = ApiVersions.V1)
    suspend fun checkLlmProvider(
        @RequestParam(defaultValue = "NVIDIA")
        keyword: String
    ): ResponseEntity<ApiResponse<*>> {
        return runCatching<ResponseEntity<ApiResponse<*>>> {
            // 1. LLM provider router를 실제 호출해 최소 키워드 확장 응답을 받는다.
            val result = llmProviderPort.expandKeyword(
                keyword = AiKeyword.of(keyword),
                maxExpansions = HEALTH_CHECK_MAX_EXPANSIONS
            )

            // 2. 호출자가 provider/model/token 사용량까지 확인할 수 있도록 메타데이터를 반환한다.
            ResponseEntity.ok<ApiResponse<*>>(
                ApiResponse.success(
                    LlmProviderHealthResponse(
                        provider = result.metadata.provider.value,
                        model = result.metadata.model.value,
                        promptVersion = result.metadata.promptVersion.value,
                        expandedKeywords = result.expandedKeywords.map { it.value },
                        inputTokens = result.metadata.tokenUsage.inputTokens,
                        outputTokens = result.metadata.tokenUsage.outputTokens
                    )
                )
            )
        }.getOrElse { error ->
            ResponseEntity.status(ErrorCode.LLM_PROVIDER_HEALTH_CHECK_FAILED.status)
                .body(ApiResponse.failure(ErrorCode.LLM_PROVIDER_HEALTH_CHECK_FAILED, error.message))
        }
    }

    private companion object {
        const val HEALTH_CHECK_MAX_EXPANSIONS = 3
    }
}
