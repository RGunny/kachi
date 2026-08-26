package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.domain.llm.LlmProviderName
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 LLM provider 차단 상태 조회/해제 API.
 *
 * 회로와 cooldown은 스스로 풀리지만 그때까지 왜 호출되지 않는지를 보는 수단이 로그뿐이라 조회를 둔다.
 * 되돌리기는 원인 해소를 확인한 운영자가 대기를 끝내는 수단이다.
 */
@RestController
class InternalLlmProviderController(
    private val llmProviderAdminPort: LlmProviderAdminPort
) {

    @GetMapping(ApiPaths.INTERNAL_LLM_PROVIDERS, version = ApiVersions.V1)
    fun findProviders(): ResponseEntity<ApiResponse<List<LlmProviderStatusResponse>>> {
        return ResponseEntity.ok(
            ApiResponse.success(llmProviderAdminPort.statuses().map(LlmProviderStatusResponse::from))
        )
    }

    @PostMapping(ApiPaths.INTERNAL_LLM_PROVIDER_RESET, version = ApiVersions.V1)
    fun reset(
        @PathVariable name: String
    ): ResponseEntity<ApiResponse<*>> {
        val provider = LlmProviderName.of(name)

        if (!llmProviderAdminPort.reset(provider)) {
            return ResponseEntity.status(ErrorCode.LLM_PROVIDER_NOT_FOUND.status)
                .body(ApiResponse.failure(ErrorCode.LLM_PROVIDER_NOT_FOUND))
        }

        // 되돌린 결과를 호출자가 바로 확인할 수 있도록 reset 후 상태를 응답한다.
        val status = llmProviderAdminPort.statuses().first { it.provider == provider }

        return ResponseEntity.ok(ApiResponse.success(LlmProviderStatusResponse.from(status)))
    }
}
