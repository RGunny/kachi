package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryExecutor
import me.rgunny.kachi.ai.adapter.inbound.news.AiNewsSummaryStarted
import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * ai-service 내부 운영용 뉴스 요약 실행 API를 제공한다.
 */
@RestController
class InternalAiNewsSummaryController(
    private val executor: AiNewsSummaryExecutor
) {

    @PostMapping(ApiPaths.INTERNAL_AI_NEWS_SUMMARIES, version = ApiVersions.V1)
    suspend fun summarizeNews(
        @RequestBody(required = false) request: SummarizeNewsRequest?
    ): ResponseEntity<ApiResponse<*>> {
        // 1. 요청 body를 뉴스 요약 command로 변환하고 입력 오류는 400으로 반환한다.
        val command = runCatching {
            (request ?: SummarizeNewsRequest()).toCommand()
        }.getOrElse { error ->
            return ResponseEntity.status(ErrorCode.INVALID_NEWS_SUMMARY_REQUEST.status)
                .body(ApiResponse.failure(ErrorCode.INVALID_NEWS_SUMMARY_REQUEST, error.message))
        }

        log.info(
            "Manual news summary requested: keywords={}, window={}, maxArticlesPerKeyword={}",
            command.keywords.size,
            command.window,
            command.maxArticlesPerKeyword
        )

        // 2. 중복 실행 방지 컴포넌트에 위임하고 실행 여부에 따라 응답을 분기한다.
        return when (val result = executor.execute(command)) {
            is AiNewsSummaryAlreadyRunning -> {
                log.info(
                    "Manual news summary skipped because another summary is running: startedAt={}",
                    result.runningSummary.startedAt
                )

                // 3. 이미 실행 중이면 클라이언트가 재시도 여부를 판단할 수 있도록 409를 반환한다.
                ResponseEntity.status(ErrorCode.NEWS_SUMMARY_ALREADY_RUNNING.status)
                    .body(ApiResponse.failure(ErrorCode.NEWS_SUMMARY_ALREADY_RUNNING))
            }

            is AiNewsSummaryStarted -> {
                log.info(
                    "Manual news summary finished: runId={}, status={}, succeeded={}, failures={}",
                    result.result.runId.value,
                    result.result.status,
                    result.result.succeededCount,
                    result.result.failureCount
                )

                // 4. 실행이 시작되어 완료된 결과를 내부 API 응답 DTO로 변환한다.
                ResponseEntity.ok(ApiResponse.success(NewsSummaryRunResponse.from(result.result)))
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalAiNewsSummaryController::class.java)
    }
}
