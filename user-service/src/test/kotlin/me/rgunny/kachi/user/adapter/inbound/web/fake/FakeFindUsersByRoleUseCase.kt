package me.rgunny.kachi.user.adapter.inbound.web.fake

import me.rgunny.kachi.user.application.port.inbound.internal.FindUsersByRoleUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindUsersByRoleQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.UserChannelsResult
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId

/**
 * 역할별 수신자 조회 유스케이스 대역.
 *
 * SLACK·TELEGRAM 채널을 가진 사용자 하나를 돌려주고 질의를 [query]에 남긴다.
 */
class FakeFindUsersByRoleUseCase : FindUsersByRoleUseCase {
    var exception: RuntimeException? = null
    lateinit var query: FindUsersByRoleQuery

    override fun findUsersByRole(query: FindUsersByRoleQuery): List<UserChannelsResult> {
        exception?.let { throw it }
        this.query = query

        return listOf(
            UserChannelsResult(
                userId = UserId.newId(),
                channels = setOf(SubscriptionChannel.TELEGRAM, SubscriptionChannel.SLACK)
            )
        )
    }
}
