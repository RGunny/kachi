package me.rgunny.kachi.user.application.port.inbound.internal

import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult

/**
 * 키워드의 수신자 목록.
 *
 * 요약 1건을 사용자가 등록한 채널들로 보내는 라우팅이 호출한다.
 */
interface FindSubscribersUseCase {

    fun findSubscribers(query: FindSubscribersQuery): List<SubscriberResult>
}
