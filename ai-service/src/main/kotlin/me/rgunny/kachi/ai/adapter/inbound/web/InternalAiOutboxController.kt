package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.ai.application.port.inbound.outbox.FindAiOutboxesUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.RecoverAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.FindAiOutboxesQuery
import me.rgunny.kachi.ai.application.port.inbound.outbox.model.RecoverAiOutboxCommand
import me.rgunny.kachi.ai.domain.outbox.AiOutboxId
import me.rgunny.kachi.ai.domain.outbox.AiOutboxStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * 내부 운영용 outbox 조회/복구 API.
 *
 * 복구는 상태 전이까지만 한다. 발행 경로는 relay 하나뿐이라 즉시 발행 API는 두지 않는다.
 */
@RestController
class InternalAiOutboxController(
    private val findAiOutboxesUseCase: FindAiOutboxesUseCase,
    private val recoverAiOutboxUseCase: RecoverAiOutboxUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_AI_OUTBOXES, version = ApiVersions.V1)
    suspend fun findOutboxes(
        @RequestParam(required = false) status: AiOutboxStatus?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<AiOutboxResponse>>> {
        val result = findAiOutboxesUseCase.find(
            FindAiOutboxesQuery(
                status = status ?: FindAiOutboxesQuery.DEFAULT_STATUS,
                limit = limit ?: FindAiOutboxesQuery.DEFAULT_LIMIT
            )
        )

        return ResponseEntity.ok(ApiResponse.success(result.outboxes.map(AiOutboxResponse::from)))
    }

    @PostMapping(ApiPaths.INTERNAL_AI_OUTBOX_RECOVER, version = ApiVersions.V1)
    suspend fun recover(
        @PathVariable outboxId: UUID
    ): ResponseEntity<ApiResponse<RecoverAiOutboxResponse>> {
        val result = recoverAiOutboxUseCase.recover(RecoverAiOutboxCommand(AiOutboxId.of(outboxId)))

        return ResponseEntity.ok(ApiResponse.success(RecoverAiOutboxResponse.from(result)))
    }
}
