package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.ChannelBindingNotActiveException
import me.rgunny.kachi.user.application.exception.DuplicateSubscriptionException
import me.rgunny.kachi.user.application.exception.SubscriptionAccessDeniedException
import me.rgunny.kachi.user.application.exception.SubscriptionNotFoundException
import me.rgunny.kachi.user.application.port.inbound.subscription.RegisterSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.UpdateSubscriptionUseCase
import me.rgunny.kachi.user.application.port.inbound.subscription.model.RegisterSubscriptionCommand
import me.rgunny.kachi.user.application.port.inbound.subscription.model.SubscriptionResult
import me.rgunny.kachi.user.application.port.inbound.subscription.model.UpdateSubscriptionCommand
import me.rgunny.kachi.user.application.port.outbound.binding.ChannelBindingPersistencePort
import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import me.rgunny.kachi.user.application.port.outbound.subscription.SubscriptionPersistencePort
import me.rgunny.kachi.user.domain.CanonicalKey
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * 구독 등록·수정.
 *
 * 등록은 원문을 정규화해 canonical 키워드를 찾거나 만들고, 그 키워드에 사용자의 구독을 건다. 키워드 생성과 구독 저장이
 * 한 트랜잭션이라 키워드만 남고 구독이 없는 상태가 생기지 않는다. 같은 사용자의 같은 canonical 키워드는 한 번만 구독된다.
 * 구독 채널은 등록·변경 시점에 그 사용자의 바인딩이 ACTIVE여야 한다.
 * 바인딩이 나중에 해지돼도 구독은 그대로 두고, 수신처 조회가 그 채널을 걸러낸다.
 */
@Service
@Transactional
class SubscriptionCommandService(
    private val keywordPersistencePort: KeywordPersistencePort,
    private val subscriptionPersistencePort: SubscriptionPersistencePort,
    private val channelBindingPersistencePort: ChannelBindingPersistencePort,
    private val activeUserValidator: ActiveUserValidator,
    private val clock: Clock
) : RegisterSubscriptionUseCase, UpdateSubscriptionUseCase {

    override fun register(command: RegisterSubscriptionCommand): SubscriptionResult {
        // 1. 활성 사용자만, 연결된 채널로만 구독할 수 있다.
        activeUserValidator.get(command.userId)
        requireActiveBindings(command.userId, command.channels)

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
            requireActiveBindings(subscription.userId, command.channels)
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

    private fun requireActiveBindings(userId: UserId, channels: Set<SubscriptionChannel>) {
        channels.sorted().forEach { channel ->
            val binding = channelBindingPersistencePort.findByUserIdAndChannel(userId, channel)

            if (binding == null || !binding.isActive) {
                throw ChannelBindingNotActiveException(userId, channel)
            }
        }
    }

    private fun findOrCreateKeyword(displayName: KeywordName, now: Instant): Keyword {
        val canonicalKey = CanonicalKey.of(displayName.value)

        return keywordPersistencePort.findByCanonicalKey(canonicalKey)
            ?: keywordPersistencePort.save(Keyword.create(displayName = displayName, createdAt = now))
    }
}
