package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.internal.FindSubscribersUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 수신자 조회 유스케이스 대역.
 *
 * SLACK 수신자 하나를 돌려주고 질의를 [query]에 남긴다.
 */
class FakeFindSubscribersUseCase : FindSubscribersUseCase {
    var exception: RuntimeException? = null
    lateinit var query: FindSubscribersQuery

    override fun findSubscribers(query: FindSubscribersQuery): List<SubscriberResult> {
        exception?.let { throw it }
        this.query = query

        return listOf(
            SubscriberResult(
                userId = UserId.newId(),
                channel = SubscriptionChannel.SLACK,
                recipientRef = ChannelBindingId.newId()
            )
        )
    }
}
