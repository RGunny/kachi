package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.application.port.inbound.watermark.FindSummaryWatermarksUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 요약 진행 지점 조회 API.
 *
 * 되돌리기는 저장된 값을 직접 고치는 절차로 하므로 후진 API는 두지 않는다(ADR 020).
 */
@RestController
class InternalSummaryWatermarkController(
    private val findSummaryWatermarksUseCase: FindSummaryWatermarksUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_AI_SUMMARY_WATERMARKS, version = ApiVersions.V1)
    suspend fun findWatermarks(): ResponseEntity<ApiResponse<List<SummaryWatermarkResponse>>> {
        val result = findSummaryWatermarksUseCase.find()

        return ResponseEntity.ok(
            ApiResponse.success(result.watermarks.map(SummaryWatermarkResponse::from))
        )
    }
}
