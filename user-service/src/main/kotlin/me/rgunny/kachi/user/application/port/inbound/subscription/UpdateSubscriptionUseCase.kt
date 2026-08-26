package me.rgunny.kachi.user.application.port.inbound.subscription

import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand

/**
 * 키워드 구독 수정.
 *
 * 채널 변경과 활성·비활성 전환만 다룬다. 소유자가 아니면 거부한다.
 */
interface UpdateSubscriptionUseCase {

    fun update(command: UpdateSubscriptionCommand): SubscriptionResult
}
