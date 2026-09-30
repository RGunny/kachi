package me.rgunny.kachi.story.adapter.inbound.web

import java.util.UUID
import me.rgunny.kachi.story.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.story.application.port.inbound.outbox.FindStoryOutboxesUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.RecoverStoryOutboxUseCase
import me.rgunny.kachi.story.application.port.inbound.outbox.model.FindStoryOutboxesQuery
import me.rgunny.kachi.story.application.port.inbound.outbox.model.RecoverStoryOutboxCommand
import me.rgunny.kachi.story.domain.outbox.StoryOutboxId
import me.rgunny.kachi.story.domain.outbox.StoryOutboxStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영용 outbox 조회·복구 API.
 *
 * 복구는 상태 전이까지만 한다. 발행 경로는 relay 하나뿐이라 즉시 발행 API는 두지 않는다.
 */
@RestController
class InternalStoryOutboxController(
    private val findStoryOutboxesUseCase: FindStoryOutboxesUseCase,
    private val recoverStoryOutboxUseCase: RecoverStoryOutboxUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_STORY_OUTBOXES, version = ApiVersions.V1)
    suspend fun findOutboxes(
        @RequestParam(required = false) status: StoryOutboxStatus?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<StoryOutboxResponse>>> {
        val result = findStoryOutboxesUseCase.find(
            FindStoryOutboxesQuery(
                status = status ?: FindStoryOutboxesQuery.DEFAULT_STATUS,
                limit = limit ?: FindStoryOutboxesQuery.DEFAULT_LIMIT
            )
        )

        return ResponseEntity.ok(ApiResponse.success(result.outboxes.map(StoryOutboxResponse::from)))
    }

    @PostMapping(ApiPaths.INTERNAL_STORY_OUTBOX_RECOVER, version = ApiVersions.V1)
    suspend fun recover(
        @PathVariable outboxId: UUID
    ): ResponseEntity<ApiResponse<RecoverStoryOutboxResponse>> {
        val result = recoverStoryOutboxUseCase.recover(RecoverStoryOutboxCommand(StoryOutboxId.of(outboxId)))

        return ResponseEntity.ok(ApiResponse.success(RecoverStoryOutboxResponse.from(result)))
    }
}
