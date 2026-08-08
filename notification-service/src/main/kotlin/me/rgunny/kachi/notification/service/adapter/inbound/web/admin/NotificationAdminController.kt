package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import me.rgunny.kachi.notification.application.port.inbound.admin.model.DeadNotificationQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.NotificationHistoryQuery
import me.rgunny.kachi.notification.application.port.inbound.admin.model.RecoverDeadNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.admin.NotificationAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiPaths
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiVersions
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.request.RecoverDeadNotificationRequest
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.NotificationAdminListResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.NotificationHistoryListResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.RecoverDeadNotificationResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Notification 현재 상태 document 운영 API adapter.
 *
 * DEAD notification 조회, 상태 history 조회, 수동 복구 요청을 core admin use case로 전달한다.
 */
@RestController
class NotificationAdminController(
    private val notificationAdminUseCase: NotificationAdminUseCase,
) {

    /**
     * 운영자가 복구 대상을 고를 수 있도록 DEAD notification 목록을 조회한다.
     */
    @GetMapping(ApiPaths.ADMIN_NOTIFICATIONS, version = ApiVersions.V1)
    suspend fun findDead(
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<NotificationAdminListResponse>> {
        val result = notificationAdminUseCase.findDead(
            DeadNotificationQuery(batchSize = limit)
        )

        return ResponseEntity.ok(
            ApiResponse.success(NotificationAdminListResponse.from(result))
        )
    }

    /**
     * 운영자가 복구 전후 상태 전이 근거를 확인할 수 있도록 history를 조회한다.
     */
    @GetMapping(ApiPaths.ADMIN_NOTIFICATION_HISTORIES, version = ApiVersions.V1)
    suspend fun findHistories(
        @PathVariable notificationId: UUID,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ResponseEntity<ApiResponse<NotificationHistoryListResponse>> {
        val result = notificationAdminUseCase.findHistories(
            NotificationHistoryQuery(
                notificationId = NotificationId.of(notificationId),
                batchSize = limit,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.success(NotificationHistoryListResponse.from(result))
        )
    }

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
