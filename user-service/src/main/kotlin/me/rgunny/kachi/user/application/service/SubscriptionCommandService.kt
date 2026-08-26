package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateSubscriptionException
import me.rgunny.kachi.user.application.exception.SubscriptionAccessDeniedException
import me.rgunny.kachi.user.application.exception.SubscriptionNotFoundException
import me.rgunny.kachi.user.application.port.inbound.subscription.RegisterSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.UpdateSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.RegisterSubscriptionCommand
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import me.rgunny.kachi.user.application.port.outbound.subscription.SubscriptionPersistencePort
import me.rgunny.kachi.user.domain.CanonicalKey
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Subscription
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 구독 등록·수정.
 *
 * 등록은 원문을 정규화해 canonical 키워드를 찾거나 만들고, 그 키워드에 사용자의 구독을 건다. 키워드 생성과 구독 저장이
 * 한 트랜잭션이라 키워드만 남고 구독이 없는 상태가 생기지 않는다. 같은 사용자의 같은 canonical 키워드는 한 번만 구독된다.
 */
@Service
@Transactional
class SubscriptionCommandService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val subscriptionPersistencePort: SubscriptionPersistencePort,
    private val activeUserValidator: ActiveUserValidator,
    private val clock: Clock
) : RegisterSubscriptionUseCase, UpdateSubscriptionUseCase {

    override fun register(command: RegisterSubscriptionCommand): SubscriptionResult {
        // 1. 활성 사용자만 구독할 수 있다.
        activeUserValidator.get(command.userId)

        val displayName = KeywordName.of(command.name)
        val now = Instant.now(clock)
        // 2. 표기가 달라도 정규화 값이 같으면 같은 키워드다.
        val keyword = findOrCreateKeyword(displayName, now)

        // 3. 중복 판정은 canonical 키워드 id 기준이다. 메시지에는 사용자가 알아볼 최초 등록 원문을 싣는다.
        if (subscriptionPersistencePort.existsByUserIdAndKeywordId(command.userId, keyword.id)) {
            throw DuplicateSubscriptionException(command.userId, keyword.displayName)
        }

        val subscription = Subscription.create(
            userId = command.userId,
            keywordId = keyword.id,
            channels = command.channels,
            registeredAt = now
        )

        return SubscriptionResult.of(subscriptionPersistencePort.save(subscription), keyword)
    }

    /** 소유자만 수정할 수 있고, 채널과 활성 여부를 각각 독립적으로 바꾼다. 응답에 키워드 이름을 싣기 위해 키워드를 다시 읽는다. */
    override fun update(command: UpdateSubscriptionCommand): SubscriptionResult {
        val subscription = subscriptionPersistencePort.findById(command.subscriptionId)
            ?: throw SubscriptionNotFoundException(command.subscriptionId)

        if (subscription.userId != command.userId) {
            throw SubscriptionAccessDeniedException(command.subscriptionId, command.userId)
        }

        activeUserValidator.get(subscription.userId)

        var updated = subscription

        if (command.channels != null) {
            updated = updated.changeChannels(command.channels)
        }

        if (command.enabled != null) {
            updated = if (command.enabled) {
                updated.enable()
            } else {
                updated.disable(Instant.now(clock))
            }
        }

        val keyword = requireNotNull(keywordPersistencePort.findById(subscription.keywordId)) {
            "구독이 참조하는 키워드가 없습니다: ${subscription.keywordId.value}"
        }

        return SubscriptionResult.of(subscriptionPersistencePort.save(updated), keyword)
    }

    private fun findOrCreateKeyword(displayName: KeywordName, now: Instant): Keyword {
        val canonicalKey = CanonicalKey.of(displayName.value)

        return keywordPersistencePort.findByCanonicalKey(canonicalKey)
            ?: keywordPersistencePort.save(Keyword.create(displayName = displayName, createdAt = now))
    }
}
