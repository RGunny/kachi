package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.outbound.llm.LlmProviderAdminPort
import me.rgunny.kachi.ai.domain.llm.LlmModel
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 LLM 모델 상태 조회·차단 해제·실제 호출 확인(probe) API.
 *
 * 회로와 cooldown은 스스로 풀리지만 그때까지 왜 호출되지 않는지를 보는 수단이 로그뿐이라 조회를 둔다.
 * 해제는 원인 해소를 확인한 운영자가 대기를 끝내는 수단이고, probe는 후보 순회 없이 지정한 모델 하나에 실제 요청을 보내 응답 여부를 확인하는 수단이다.
 *
 * 대상은 [LlmModel] 상수명으로 지정한다. 상수가 아닌 값은 바인딩 단계에서 400으로 끝나고,
 * 상수지만 어느 용도의 후보도 아닌 모델은 404다.
 */
@RestController
class InternalLlmModelController(
    private val llmProviderAdminPort: LlmProviderAdminPort
) {

    @GetMapping(ApiPaths.INTERNAL_LLM_MODELS, version = ApiVersions.V1)
    fun findModels(): ResponseEntity<ApiResponse<List<LlmModelStatusResponse>>> {
        return ResponseEntity.ok(
            ApiResponse.success(llmProviderAdminPort.statuses().map(LlmModelStatusResponse::from))
        )
    }

    @PostMapping(ApiPaths.INTERNAL_LLM_MODEL_RESET, version = ApiVersions.V1)
    fun reset(
        @PathVariable model: LlmModel
    ): ResponseEntity<ApiResponse<*>> {
        if (!llmProviderAdminPort.reset(model)) {
            return notCandidate()
        }

        // 되돌린 결과를 호출자가 바로 확인할 수 있도록 reset 후 상태를 응답한다.
        val status = llmProviderAdminPort.statuses().first { it.model == model }

        return ResponseEntity.ok(ApiResponse.success(LlmModelStatusResponse.from(status)))
    }

    /**
     * 실패는 [me.rgunny.kachi.ai.application.exception.LlmProviderException]이 그대로 올라가 502로 응답된다.
     * 차단 중인 모델도 같은 경로다. 응답 메시지에 실패 코드와 차단 사유가 실린다.
     */
    @PostMapping(ApiPaths.INTERNAL_LLM_MODEL_PROBE, version = ApiVersions.V1)
    suspend fun probe(
        @PathVariable model: LlmModel
    ): ResponseEntity<ApiResponse<*>> {
        val result = llmProviderAdminPort.probe(model) ?: return notCandidate()

        return ResponseEntity.ok(ApiResponse.success(LlmProbeResponse.from(result)))
    }

    private fun notCandidate(): ResponseEntity<ApiResponse<*>> {
        return ResponseEntity.status(ErrorCode.LLM_MODEL_NOT_CANDIDATE.status)
            .body(ApiResponse.failure(ErrorCode.LLM_MODEL_NOT_CANDIDATE))
    }
}
