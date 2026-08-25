package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindKeywordQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseKeywordQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindKeywordQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseKeywordQuarantineCommand
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.domain.quarantine.KeywordQuarantineStatus
import me.rgunny.kachi.ai.domain.run.AiRunTargetType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 키워드 격리 조회/해제 API.
 *
 * HTTP 요청과 응답 변환만 하고, 조회 조건 해석과 해제 전이는 유스케이스에 맡긴다.
 */
@RestController
class InternalKeywordQuarantineController(
    private val findKeywordQuarantinesUseCase: FindKeywordQuarantinesUseCase,
    private val releaseKeywordQuarantineUseCase: ReleaseKeywordQuarantineUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_AI_KEYWORD_QUARANTINES, version = ApiVersions.V1)
    suspend fun findQuarantines(
        @RequestParam(required = false) targetType: AiRunTargetType?,
        @RequestParam(required = false) status: KeywordQuarantineStatus?
    ): ResponseEntity<ApiResponse<List<KeywordQuarantineResponse>>> {
        val result = findKeywordQuarantinesUseCase.find(
            FindKeywordQuarantinesQuery(targetType = targetType, status = status)
        )

        return ResponseEntity.ok(
            ApiResponse.success(result.quarantines.map(KeywordQuarantineResponse::from))
        )
    }

    /**
     * 격리를 만드는 곳이 뉴스 요약뿐이라 대상 종류는 기본값을 둔다.
     */
    @PostMapping(ApiPaths.INTERNAL_AI_KEYWORD_QUARANTINE_RELEASE, version = ApiVersions.V1)
    suspend fun release(
        @PathVariable keyword: String,
        @RequestParam(defaultValue = DEFAULT_TARGET_TYPE) targetType: AiRunTargetType
    ): ResponseEntity<ApiResponse<KeywordQuarantineResponse>> {
        val result = releaseKeywordQuarantineUseCase.release(
            ReleaseKeywordQuarantineCommand(targetType = targetType, keyword = AiKeyword.of(keyword))
        )

        return ResponseEntity.ok(ApiResponse.success(KeywordQuarantineResponse.from(result.quarantine)))
    }

    private companion object {
        const val DEFAULT_TARGET_TYPE = "NEWS_SUMMARY"
    }
}
