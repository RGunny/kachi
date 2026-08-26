package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.internal.ResolveChannelBindingUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolveChannelBindingQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.ResolvedChannelBindingResult
import me.rgunny.kachi.user.domain.ChannelBindingStatus
import me.rgunny.kachi.user.domain.SubscriptionChannel

/**
 * 바인딩 참조 조회 유스케이스 대역.
 *
 * ACTIVE SLACK 주소를 돌려주고 질의를 [query]에 남긴다.
 * [exception]을 넣으면 그 예외를 던진다.
 */
class FakeResolveChannelBindingUseCase : ResolveChannelBindingUseCase {
    var exception: RuntimeException? = null
    lateinit var query: ResolveChannelBindingQuery

    override fun resolve(query: ResolveChannelBindingQuery): ResolvedChannelBindingResult {
        exception?.let { throw it }
        this.query = query

        return ResolvedChannelBindingResult(
            channel = SubscriptionChannel.SLACK,
            status = ChannelBindingStatus.ACTIVE,
            address = "https://hooks.slack.com/services/T000/B000/XXXX"
        )
    }
}
