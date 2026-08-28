package me.rgunny.kachi.notification.routing.fake

import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.NotificationRequestPublisherPort
import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model.NotificationRequest

/**
 * 발행한 요청을 기록하는 publisher.
 * failAt번째(0부터) 발행에서 예외를 던져 중간 실패를 흉내 낸다.
 */
class FakeNotificationRequestPublisherPort : NotificationRequestPublisherPort {
    val published = mutableListOf<NotificationRequest>()
    var failAt: Int? = null

    override suspend fun publish(request: NotificationRequest) {
        if (failAt == published.size) {
            throw IllegalStateException("publish failed. requestId=${request.requestId}")
        }
        published += request
    }
}
