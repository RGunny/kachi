package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.keyword.AiKeywordExpansionAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.keyword.AiKeywordExpansionExecutor
import me.rgunny.kachi.ai.adapter.inbound.keyword.AiKeywordExpansionLockUnavailable
import me.rgunny.kachi.ai.adapter.inbound.keyword.AiKeywordExpansionStarted
import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * ai-service 내부 운영용 키워드 확장 실행 API를 제공한다.
 */
@RestController
class InternalAiKeywordExpansionController(
    private val executor: AiKeywordExpansionExecutor
) {

    @PostMapping(ApiPaths.INTERNAL_AI_KEYWORD_EXPANSIONS, version = ApiVersions.V1)
    suspend fun expandKeywords(
        @RequestBody(required = false) request: ExpandKeywordsRequest?
    ): ResponseEntity<ApiResponse<*>> {
        // 1. 요청 body를 키워드 확장 command로 변환하고 입력 오류는 400으로 반환한다.
        val command = runCatching {
            (request ?: ExpandKeywordsRequest()).toCommand()
        }.getOrElse { error ->
            return ResponseEntity.status(ErrorCode.INVALID_KEYWORD_EXPANSION_REQUEST.status)
                .body(ApiResponse.failure(ErrorCode.INVALID_KEYWORD_EXPANSION_REQUEST, error.message))
        }

        log.info(
            "Manual keyword expansion requested: keywords={}, maxExpansionsPerKeyword={}",
            command.keywords.size,
            command.maxExpansionsPerKeyword
        )

        // 2. 중복 실행 방지 컴포넌트에 위임하고 실행 여부에 따라 응답을 분기한다.
        return when (val result = executor.execute(command)) {
            is AiKeywordExpansionAlreadyRunning -> {
                log.info(
                    "Manual keyword expansion skipped because another expansion is running: startedAt={}",
                    result.runningExpansion.startedAt
                )

                // 3. 이미 실행 중이면 클라이언트가 재시도 여부를 판단할 수 있도록 409를 반환한다.
                ResponseEntity.status(ErrorCode.KEYWORD_EXPANSION_ALREADY_RUNNING.status)
                    .body(ApiResponse.failure(ErrorCode.KEYWORD_EXPANSION_ALREADY_RUNNING))
            }

            is AiKeywordExpansionStarted -> {
                log.info(
                    "Manual keyword expansion finished: runId={}, status={}, succeeded={}, failures={}",
                    result.result.runId.value,
                    result.result.status,
                    result.result.succeededCount,
                    result.result.failureCount
                )

                // 4. 실행이 시작되어 완료된 결과를 내부 API 응답 DTO로 변환한다.
                ResponseEntity.ok(ApiResponse.success(KeywordExpansionRunResponse.from(result.result)))
            }

            is AiKeywordExpansionLockUnavailable -> {
                log.warn(
                    "Manual keyword expansion skipped because the execution lock could not be checked",
                    result.cause
                )

                // 5. 이미 실행 중이라 막힌 것과 달리 장애이므로 503으로 구분해 알린다.
                ResponseEntity.status(ErrorCode.KEYWORD_EXPANSION_LOCK_UNAVAILABLE.status)
                    .body(ApiResponse.failure(ErrorCode.KEYWORD_EXPANSION_LOCK_UNAVAILABLE))
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalAiKeywordExpansionController::class.java)
    }
}
