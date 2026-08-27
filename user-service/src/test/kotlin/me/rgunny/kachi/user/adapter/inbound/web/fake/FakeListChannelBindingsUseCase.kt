package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.binding.ListChannelBindingsUseCase
import me.rgunny.kachi.user.application.port.inbound.binding.model.ChannelBindingResult
import me.rgunny.kachi.user.application.port.inbound.binding.model.ListChannelBindingsQuery
import me.rgunny.kachi.user.domain.ChannelBindingId
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.fixture.UserTestFixture

/**
 * 바인딩 목록 유스케이스 대역.
 *
 * ACTIVE SLACK 하나를 돌려주고 질의를 [query]에 남긴다.
 */
class FakeListChannelBindingsUseCase : ListChannelBindingsUseCase {
    var exception: RuntimeException? = null
    lateinit var query: ListChannelBindingsQuery

    override fun list(query: ListChannelBindingsQuery): List<ChannelBindingResult> {
        exception?.let { throw it }
        this.query = query

        return listOf(
            ChannelBindingResult(
                id = ChannelBindingId.newId(),
                channel = SubscriptionChannel.SLACK,
                status = ChannelBindingStatus.ACTIVE,
                addressMasked = "https://hooks.slack.com/****",
                boundAt = UserTestFixture.NOW,
                revokedAt = null
            )
        )
    }
}
