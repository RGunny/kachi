package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.inbound.subscription.ListSubscriptionsUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.ListSubscriptionsQuery
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import me.rgunny.kachi.user.application.port.outbound.subscription.SubscriptionPersistencePort
import org.springframework.stereotype.Service

/**
 * 내 구독 목록 조회.
 *
 * 구독은 keywordId만 가지므로 응답에 이름·canonicalKey를 싣기 위해 키워드를 한 번에 읽어 짝짓는다.
 */
@Service
class SubscriptionQueryService(
    private val subscriptionPersistencePort: SubscriptionPersistencePort,
    private val keywordPersistencePort: KeywordPersistencePort,
    private val activeUserValidator: ActiveUserValidator
) : ListSubscriptionsUseCase {

    override fun list(query: ListSubscriptionsQuery): List<SubscriptionResult> {
        activeUserValidator.get(query.userId)

        val subscriptions = subscriptionPersistencePort.findAllByUserId(query.userId)
            .sortedByDescending { it.registeredAt }
        val keywordsById = keywordPersistencePort.findAllByIds(subscriptions.map { it.keywordId }.toSet())
            .associateBy { it.id }

        return subscriptions.map { subscription ->
            val keyword = requireNotNull(keywordsById[subscription.keywordId]) {
                "구독이 참조하는 키워드가 없습니다: ${subscription.keywordId.value}"
            }
            SubscriptionResult.of(subscription, keyword)
        }
    }
}
