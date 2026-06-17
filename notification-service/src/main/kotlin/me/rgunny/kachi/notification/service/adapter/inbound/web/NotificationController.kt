package me.rgunny.kachi.notification.service.adapter.inbound.web

import jakarta.validation.Valid
import me.rgunny.kachi.notification.application.port.inbound.RequestNotificationUseCase
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class NotificationController(
    private val requestNotificationUseCase: RequestNotificationUseCase,
) {

    @PostMapping(ApiPaths.NOTIFICATIONS, version = ApiVersions.V1)
    suspend fun request(
        @Valid @RequestBody request: NotificationRequest,
    ): ResponseEntity<ApiResponse<NotificationResponse>> {
        val result = requestNotificationUseCase.request(request.toCommand())

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(NotificationResponse.from(result)))
    }
}
