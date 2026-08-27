package me.rgunny.kachi.notification.fake

import me.rgunny.kachi.notification.application.port.inbound.request.RequestNotificationUseCase
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationCommand
import me.rgunny.kachi.notification.application.port.inbound.request.model.RequestNotificationResult
import me.rgunny.kachi.notification.domain.NotificationId
import me.rgunny.kachi.notification.domain.NotificationStatus
import java.time.Instant

/**
 * 접수 호출을 기록하는 fake. duplicatedRequestIds에 든 requestId는 중복으로, failAt 번째 호출은 실패로 응답한다.
 */
class FakeRequestNotificationUseCase(
    private val now: Instant,
) : RequestNotificationUseCase {
    val commands = mutableListOf<RequestNotificationCommand>()
    val duplicatedRequestIds = mutableSetOf<String>()
    var failAt: Int? = null
    var failure: RuntimeException = IllegalStateException("request failed")

    override suspend fun request(command: RequestNotificationCommand): RequestNotificationResult {
        commands += command
        if (failAt == commands.size - 1) {
            throw failure
        }
        return RequestNotificationResult(
            notificationId = NotificationId.newId(),
            status = NotificationStatus.REQUESTED,
            duplicated = command.requestId in duplicatedRequestIds,
            acceptedAt = now,
        )
    }
}
