package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.subscription.ListSubscriptionsUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.ListSubscriptionsQuery
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.SubscriptionId
import me.rgunny.kachi.user.fixture.UserTestFixture

/**
 * 구독 목록 유스케이스 대역.
 * 질의의 userId로 구독 한 건을 돌려주고 질의를 [query]에 남긴다.
 */
class FakeListSubscriptionsUseCase : ListSubscriptionsUseCase {
    var exception: RuntimeException? = null
    lateinit var query: ListSubscriptionsQuery

    override fun list(query: ListSubscriptionsQuery): List<SubscriptionResult> {
        exception?.let { throw it }
        this.query = query

        return listOf(
            SubscriptionResult(
                id = SubscriptionId.newId(),
                userId = query.userId,
                keywordId = KeywordId.newId(),
                name = "Trump",
                canonicalKey = "trump",
                channels = setOf(SubscriptionChannel.SLACK),
                enabled = true,
                registeredAt = UserTestFixture.NOW,
                disabledAt = null
            )
        )
    }
}
