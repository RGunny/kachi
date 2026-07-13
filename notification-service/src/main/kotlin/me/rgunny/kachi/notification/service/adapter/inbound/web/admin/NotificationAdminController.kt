package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import me.rgunny.kachi.notification.application.port.dto.admin.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.NotificationAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiPaths
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiVersions
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request.RecoverDeadNotificationRequest
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.RecoverDeadNotificationResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Notification 현재 상태 document 운영 API adapter.
 *
 * DEAD notification 수동 복구 요청을 core admin use case로 전달한다.
 */
@RestController
class NotificationAdminController(
    private val notificationAdminUseCase: NotificationAdminUseCase,
) {

    /**
     * DEAD notification을 REQUESTED로 복구하고 새 dispatch outbox를 생성한다.
     */
    @PostMapping(ApiPaths.ADMIN_NOTIFICATION_RECOVER, version = ApiVersions.V1)
    suspend fun recoverDead(
        @PathVariable notificationId: UUID,
        @RequestBody(required = false) request: RecoverDeadNotificationRequest?,
    ): ResponseEntity<ApiResponse<RecoverDeadNotificationResponse>> {
        val result = notificationAdminUseCase.recoverDead(
            RecoverDeadNotificationCommand(
                notificationId = NotificationId.of(notificationId),
                reason = request?.reason ?: RecoverDeadNotificationRequest.DEFAULT_REASON,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.success(RecoverDeadNotificationResponse.from(result))
        )
    }
}
