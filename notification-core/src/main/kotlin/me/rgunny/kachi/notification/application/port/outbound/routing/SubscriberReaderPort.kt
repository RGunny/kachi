package me.rgunny.kachi.notification.application.port.outbound.routing

import me.rgunny.kachi.notification.application.port.outbound.routing.model.Subscriber

/**
 * 키워드 구독자 조회 port.
 *
 * 구현체는 발송 가능한 (사용자, 채널, 수신처 참조)만 돌려준다. 없는 키워드는 빈 목록이다.
 * 조회 실패는 SubscriberReaderException으로 던진다.
 */
interface SubscriberReaderPort {

    suspend fun findSubscribers(keyword: String): List<Subscriber>
}
