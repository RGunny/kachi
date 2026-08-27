package me.rgunny.kachi.notification.routing.application.port.outbound.messaging

import me.rgunny.kachi.notification.routing.application.port.outbound.messaging.model.NotificationRequest

/**
 * 알림 요청 1건을 접수 topic으로 발행하는 port.
 *
 * broker ack까지 기다린 뒤 돌아온다.
 * 실패는 예외로 전파하고 호출자가 같은 요청을 다시 보낼 수 있어야 하므로 구현체는 requestId를 바꾸지 않는다.
 */
interface NotificationRequestPublisherPort {

    suspend fun publish(request: NotificationRequest)
}
