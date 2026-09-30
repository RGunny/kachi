package me.rgunny.kachi.notification.service.adapter.inbound.web

import jakarta.validation.Valid
import kotlinx.coroutines.CancellationException
import me.rgunny.kachi.notification.application.port.inbound.request.RequestNotificationUseCase
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetrics
import me.rgunny.kachi.notification.service.adapter.monitoring.NotificationServiceMetricContract.RequestSource
import me.rgunny.kachi.notification.service.adapter.inbound.web.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** HTTP 알림 요청을 core 접수 use case에 연결하고 인입 결과를 계측한다. */
@RestController
class NotificationController(
    private val requestNotificationUseCase: RequestNotificationUseCase,
    private val metrics: NotificationServiceMetrics,
) {

    /**
     * Bean Validation이 끝난 요청부터 core 접수 결과가 반환될 때까지를
     * HTTP request metric으로 기록한다.
     */
    @PostMapping(ApiPaths.NOTIFICATIONS, version = ApiVersions.V1)
    suspend fun request(
        @Valid @RequestBody request: NotificationRequest,
    ): ResponseEntity<ApiResponse<NotificationResponse>> {
        val startedAt = System.nanoTime()
        val result = try {
            requestNotificationUseCase.request(request.toCommand())
        } catch (exception: CancellationException) {
            // coroutine 취소는 업무 실패가 아니므로 metric으로 변환하지 않는다.
            throw exception
        } catch (exception: Exception) {
            metrics.recordRequestFailure(
                source = RequestSource.HTTP,
                channel = request.channel,
                elapsed = elapsed(startedAt),
            )
            throw exception
        }
        metrics.recordRequest(
            source = RequestSource.HTTP,
            channel = request.channel,
            result = result,
            elapsed = elapsed(startedAt),
        )

        return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ApiResponse.success(NotificationResponse.from(result)))
    }

    private fun elapsed(startedAt: Long): Duration {
        return Duration.ofNanos(System.nanoTime() - startedAt)
    }
}
