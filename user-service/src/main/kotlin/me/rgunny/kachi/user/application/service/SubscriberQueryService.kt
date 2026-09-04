package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.internal.FindSubscribersUseCase
import me.rgunny.kachi.user.application.port.inbound.internal.model.FindSubscribersQuery
import me.rgunny.kachi.user.application.port.inbound.internal.model.SubscriberResult
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import me.rgunny.kachi.user.application.port.outbound.subscription.SubscriptionPersistencePort
import me.rgunny.kachi.user.domain.CanonicalKey
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 키워드의 수신자 조회.
 *
 * enabled 구독의 채널마다 그 사용자의 ACTIVE 바인딩이 있을 때만 수신자로 센다.
 * 해지된 바인딩은 구독을 건드리지 않고 여기서 걸러진다.
 */
@Service
@Transactional(readOnly = true)
class SubscriberQueryService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val subscriptionPersistencePort: SubscriptionPersistencePort,
    private val channelBindingPersistencePort: ChannelBindingPersistencePort
) : FindSubscribersUseCase {

    override fun findSubscribers(query: FindSubscribersQuery): List<SubscriberResult> {
        // 1. 키워드가 없으면 수신자도 없다.
        val keyword = keywordPersistencePort.findByCanonicalKey(CanonicalKey.of(query.keyword))
            ?: return emptyList()

        // 2. enabled 구독과 그 사용자들의 바인딩을 각각 한 번에 읽는다.
        val subscriptions = subscriptionPersistencePort.findAllEnabledByKeywordId(keyword.id)
        val activeBindings = channelBindingPersistencePort
            .findAllByUserIds(subscriptions.map { it.userId }.toSet())
            .filter { it.isActive }
            .map { it.userId to it.channel }
            .toSet()

        // 3. 구독 x 채널 조합을 만들되 ACTIVE 바인딩이 있는 채널만 남긴다.
        return subscriptions
            .flatMap { subscription ->
                subscription.channels
                    .filter { channel -> (subscription.userId to channel) in activeBindings }
                    .map { channel -> SubscriberResult(userId = subscription.userId, channel = channel) }
            }
            .sortedWith(compareBy({ it.userId.value }, { it.channel }))
    }
}
