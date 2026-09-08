package me.rgunny.kachi.collector.adapter.inbound.web

import me.rgunny.kachi.collector.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.collector.application.port.inbound.outbox.FindCollectorOutboxesUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.RecoverCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.FindCollectorOutboxesQuery
import me.rgunny.kachi.collector.application.port.inbound.outbox.model.RecoverCollectorOutboxCommand
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxId
import me.rgunny.kachi.collector.domain.outbox.CollectorOutboxStatus
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
class InternalCollectorOutboxController(
    private val findCollectorOutboxesUseCase: FindCollectorOutboxesUseCase,
    private val recoverCollectorOutboxUseCase: RecoverCollectorOutboxUseCase
) {

    @GetMapping(ApiPaths.INTERNAL_COLLECTOR_OUTBOXES, version = ApiVersions.V1)
    suspend fun findOutboxes(
        @RequestParam(required = false) status: CollectorOutboxStatus?,
        @RequestParam(required = false) limit: Int?
    ): ResponseEntity<ApiResponse<List<CollectorOutboxResponse>>> {
        val result = findCollectorOutboxesUseCase.find(
            FindCollectorOutboxesQuery(
                status = status ?: FindCollectorOutboxesQuery.DEFAULT_STATUS,
                limit = limit ?: FindCollectorOutboxesQuery.DEFAULT_LIMIT
            )
        )

        return ResponseEntity.ok(ApiResponse.success(result.outboxes.map(CollectorOutboxResponse::from)))
    }

    @PostMapping(ApiPaths.INTERNAL_COLLECTOR_OUTBOX_RECOVER, version = ApiVersions.V1)
    suspend fun recover(
        @PathVariable outboxId: UUID
    ): ResponseEntity<ApiResponse<RecoverCollectorOutboxResponse>> {
        val result = recoverCollectorOutboxUseCase.recover(RecoverCollectorOutboxCommand(CollectorOutboxId.of(outboxId)))

        return ResponseEntity.ok(ApiResponse.success(RecoverCollectorOutboxResponse.from(result)))
    }
}
