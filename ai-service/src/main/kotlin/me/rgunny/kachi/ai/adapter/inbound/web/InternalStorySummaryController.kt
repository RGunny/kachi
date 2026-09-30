package me.rgunny.kachi.ai.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.ai.adapter.inbound.scheduler.AiStorySummarySchedulerSettings
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryAlreadyRunning
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryExecutor
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryLockUnavailable
import me.rgunny.kachi.ai.adapter.inbound.story.AiStorySummaryStarted
import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.adapter.inbound.web.response.ErrorCode
import me.rgunny.kachi.ai.application.port.inbound.story.FindStorySummariesUseCase
import me.rgunny.kachi.ai.application.port.inbound.story.model.FindStorySummariesQuery
import me.rgunny.kachi.ai.domain.story.StoryId
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 story 요약 조회·tick 수동 실행 API.
 *
 * HTTP 요청과 응답 변환만 하고, 조회와 실행은 유스케이스와 executor에 맡긴다.
 */
@RestController
class InternalStorySummaryController(
    private val findStorySummariesUseCase: FindStorySummariesUseCase,
    private val executor: AiStorySummaryExecutor,
    private val schedulerSettings: AiStorySummarySchedulerSettings
) {

    @GetMapping(ApiPaths.INTERNAL_AI_STORY_SUMMARIES, version = ApiVersions.V1)
    suspend fun findStorySummaries(
        @RequestParam storyId: UUID,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<StorySummaryResponse>>> {
        val result = findStorySummariesUseCase.find(
            FindStorySummariesQuery(
                storyId = StoryId.of(storyId),
                limit = limit ?: FindStorySummariesQuery.DEFAULT_LIMIT
            )
        )

        return ResponseEntity.ok(
            ApiResponse.success(result.summaries.map(StorySummaryResponse::from))
        )
    }

    @PostMapping(ApiPaths.INTERNAL_AI_STORY_SUMMARY_RUN, version = ApiVersions.V1)
    suspend fun runStorySummaries(): ResponseEntity<ApiResponse<*>> {
        log.info("Manual story summary tick requested: maxStoriesPerTick={}", schedulerSettings.maxStoriesPerTick)

        return when (val result = executor.execute(schedulerSettings.toCommand())) {
            is AiStorySummaryAlreadyRunning -> {
                log.info(
                    "Manual story summary tick skipped because another tick is running: startedAt={}",
                    result.runningSummary.startedAt
                )

                ResponseEntity.status(ErrorCode.STORY_SUMMARY_ALREADY_RUNNING.status)
                    .body(ApiResponse.failure(ErrorCode.STORY_SUMMARY_ALREADY_RUNNING))
            }

            is AiStorySummaryStarted -> {
                log.info(
                    "Manual story summary tick finished: due={}, created={}, skipped={}, failed={}, aborted={}",
                    result.result.due,
                    result.result.created,
                    result.result.skipped,
                    result.result.failed,
                    result.result.aborted
                )

                ResponseEntity.ok(ApiResponse.success(StorySummaryRunResponse.from(result.result)))
            }

            is AiStorySummaryLockUnavailable -> {
                log.warn(
                    "Manual story summary tick skipped because the execution lock could not be checked",
                    result.cause
                )

                ResponseEntity.status(ErrorCode.STORY_SUMMARY_LOCK_UNAVAILABLE.status)
                    .body(ApiResponse.failure(ErrorCode.STORY_SUMMARY_LOCK_UNAVAILABLE))
            }
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(InternalStorySummaryController::class.java)
    }
}
