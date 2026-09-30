package me.rgunny.kachi.ai.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.application.port.inbound.quarantine.FindStoryQuarantinesUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.ReleaseStoryQuarantineUseCase
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.FindStoryQuarantinesQuery
import me.rgunny.kachi.ai.application.port.inbound.quarantine.model.ReleaseStoryQuarantineCommand
import me.rgunny.kachi.ai.domain.quarantine.StoryQuarantineStatus
import me.rgunny.kachi.ai.domain.story.StoryId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 story 격리 조회/해제 API.
 *
 * HTTP 요청과 응답 변환만 하고, 조회 조건 해석과 해제 전이는 유스케이스에 맡긴다.
 */
@RestController
class InternalStoryQuarantineController(
    private val findStoryQuarantinesUseCase: FindStoryQuarantinesUseCase,
    private val releaseStoryQuarantineUseCase: ReleaseStoryQuarantineUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_AI_STORY_QUARANTINES, version = ApiVersions.V1)
    suspend fun findQuarantines(
        @RequestParam(required = false) status: StoryQuarantineStatus?
    ): ResponseEntity<ApiResponse<List<StoryQuarantineResponse>>> {
        val result = findStoryQuarantinesUseCase.find(FindStoryQuarantinesQuery(status = status))

        return ResponseEntity.ok(
            ApiResponse.success(result.quarantines.map(StoryQuarantineResponse::from))
        )
    }

    @PostMapping(ApiPaths.INTERNAL_AI_STORY_QUARANTINE_RELEASE, version = ApiVersions.V1)
    suspend fun release(
        @PathVariable storyId: UUID
    ): ResponseEntity<ApiResponse<StoryQuarantineResponse>> {
        val result = releaseStoryQuarantineUseCase.release(
            ReleaseStoryQuarantineCommand(storyId = StoryId.of(storyId))
        )

        return ResponseEntity.ok(ApiResponse.success(StoryQuarantineResponse.from(result.quarantine)))
    }
}
