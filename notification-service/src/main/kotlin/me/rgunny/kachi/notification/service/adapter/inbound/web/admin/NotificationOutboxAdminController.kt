package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import me.rgunny.kachi.notification.application.port.inbound.outbox.model.DeadNotificationOutboxQuery
import me.rgunny.kachi.notification.application.port.inbound.outbox.model.RecoverNotificationOutboxCommand
import me.rgunny.kachi.notification.application.port.inbound.outbox.NotificationOutboxAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationOutboxId
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiPaths
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiVersions
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.NotificationOutboxAdminListResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.RecoverNotificationOutboxResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * notification outbox 운영 API adapter.
 *
 * HTTP 요청/응답 변환만 담당하고, DEAD 조회와 복구 상태 전이는 core admin use case에 위임한다.
 */
@RestController
class NotificationOutboxAdminController(
    private val notificationOutboxAdminUseCase: NotificationOutboxAdminUseCase,
) {

    @GetMapping(ApiPaths.ADMIN_NOTIFICATION_OUTBOXES, version = ApiVersions.V1)
    suspend fun findDead(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<NotificationOutboxAdminListResponse>> {
        val result = notificationOutboxAdminUseCase.findDead(
            DeadNotificationOutboxQuery(batchSize = limit)
        )

        return ResponseEntity.ok(
            ApiResponse.success(NotificationOutboxAdminListResponse.from(result))
        )
    }

    @PostMapping(ApiPaths.ADMIN_NOTIFICATION_OUTBOX_RECOVER, version = ApiVersions.V1)
    suspend fun recover(
        @PathVariable outboxId: UUID,
    ): ResponseEntity<ApiResponse<RecoverNotificationOutboxResponse>> {
        val result = notificationOutboxAdminUseCase.recover(
            RecoverNotificationOutboxCommand(NotificationOutboxId.of(outboxId))
        )

        return ResponseEntity.ok(
            ApiResponse.success(RecoverNotificationOutboxResponse.from(result))
        )
    }
}
