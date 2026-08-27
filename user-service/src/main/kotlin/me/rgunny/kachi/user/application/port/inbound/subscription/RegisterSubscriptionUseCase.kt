package me.rgunny.kachi.user.application.port.inbound.subscription

import me.rgunny.kachi.user.application.port.inbound.subscription.model.RegisterSubscriptionCommand
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult

/**
 * 키워드 구독 등록.
 *
 * 원문 이름과 채널을 받아 canonical 키워드를 찾거나 만들고 구독을 건다.
 * 같은 사용자의 같은 키워드는 두 번 등록되지 않는다.
 */
interface RegisterSubscriptionUseCase {

    fun register(command: RegisterSubscriptionCommand): SubscriptionResult
}
