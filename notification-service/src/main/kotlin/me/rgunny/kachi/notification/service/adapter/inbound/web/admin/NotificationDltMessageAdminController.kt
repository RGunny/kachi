package me.rgunny.kachi.notification.service.adapter.inbound.web.admin

import me.rgunny.kachi.notification.application.port.dto.dlt.NotificationDltMessageQuery
import me.rgunny.kachi.notification.application.port.inbound.NotificationDltMessageAdminUseCase
import me.rgunny.kachi.notification.domain.NotificationDltMessageId
import me.rgunny.kachi.notification.domain.NotificationDltMessageStatus
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiPaths
import me.rgunny.kachi.notification.service.adapter.inbound.web.ApiVersions
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.NotificationDltMessageDetailResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.admin.response.NotificationDltMessageAdminListResponse
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * notification dispatch DLT 메시지 운영 조회 API adapter.
 */
@RestController
class NotificationDltMessageAdminController(
    private val useCase: NotificationDltMessageAdminUseCase,
) {

    /**
     * 운영자가 재처리/폐기 대상을 고를 수 있도록 DLT 메시지 목록을 조회한다.
     */
    @GetMapping(ApiPaths.ADMIN_NOTIFICATION_DLT_MESSAGES, version = ApiVersions.V1)
    suspend fun find(
        @RequestParam(defaultValue = "PENDING") status: NotificationDltMessageStatus,
        @RequestParam(defaultValue = "50") limit: Int,
    ): ResponseEntity<ApiResponse<NotificationDltMessageAdminListResponse>> {
        val result = useCase.find(
            NotificationDltMessageQuery(
                status = status,
                batchSize = limit,
            )
        )

        return ResponseEntity.ok(
            ApiResponse.success(NotificationDltMessageAdminListResponse.from(result))
        )
    }

    /**
     * 운영자가 원본 payload를 확인할 수 있도록 DLT 메시지 상세를 조회한다.
     */
    @GetMapping(ApiPaths.ADMIN_NOTIFICATION_DLT_MESSAGE, version = ApiVersions.V1)
    suspend fun get(
        @PathVariable messageId: UUID,
    ): ResponseEntity<ApiResponse<NotificationDltMessageDetailResponse>> {
        val result = useCase.get(NotificationDltMessageId.of(messageId))

        return ResponseEntity.ok(
            ApiResponse.success(NotificationDltMessageDetailResponse.from(result))
        )
    }
}
