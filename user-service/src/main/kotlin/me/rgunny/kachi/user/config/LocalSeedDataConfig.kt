package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.outbound.persistence.KeywordJpaEntity
import me.rgunny.kachi.user.adapter.outbound.persistence.KeywordJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.SubscriptionJpaEntity
import me.rgunny.kachi.user.adapter.outbound.persistence.SubscriptionJpaRepository
import me.rgunny.kachi.user.adapter.outbound.persistence.UserJpaEntity
import me.rgunny.kachi.user.adapter.outbound.persistence.UserJpaRepository
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.CanonicalKey
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.User
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Clock
import java.time.Instant

/**
 * 로컬 개발용 시드 데이터.
 *
 * `local` 프로파일에서만 기동 시 한 번 실행되어 시드 사용자와 키워드 구독을 심는다. 사용자 등록·로그인·구독을 손으로 하지 않아도
 * 활성 키워드 API가 채워져 수집·요약을 바로 돌릴 수 있다. 모든 단계가 find-or-create라 재기동해도 중복이 생기지 않는다.
 *
 * 유스케이스가 아니라 JPA repository를 직접 쓴다. 유스케이스를 거치면 활성 사용자·채널 바인딩 검증을 시드가 만족시켜야 하고,
 * repository를 직접 만지는 코드는 레이어 규칙상 config에만 둘 수 있다.
 */
@Configuration
@Profile("local")
class LocalSeedDataConfig {

    /** 시드 사용자 → canonical 키워드 → 구독 순으로 심고 결과를 한 줄 남긴다. */
    @Bean
    fun localSeedDataInitializer(
        userJpaRepository: UserJpaRepository,
        keywordJpaRepository: KeywordJpaRepository,
        subscriptionJpaRepository: SubscriptionJpaRepository,
        clock: Clock
    ): ApplicationRunner {
        return ApplicationRunner {
            val now = Instant.now(clock)
            val seedUser = findOrCreateSeedUser(userJpaRepository, now)
            val keywords = SEED_KEYWORD_NAMES.map { findOrCreateKeyword(keywordJpaRepository, it, now) }
            val subscriptions = keywords.map { findOrCreateSubscription(subscriptionJpaRepository, seedUser, it, now) }

            log.info(
                "Local seed data initialized userId={} subscriptionCount={} keywords={}",
                seedUser.id,
                subscriptions.size,
                keywords.map { it.canonicalKey.value }
            )
        }
    }

    /** 시드 사용자를 이메일로 찾고 없으면 LOCAL provider 사용자로 등록한다. */
    private fun findOrCreateSeedUser(userJpaRepository: UserJpaRepository, registeredAt: Instant): User {
        val existingUser = userJpaRepository.findByEmail(SEED_USER_EMAIL)
        if (existingUser != null) {
            return existingUser.toDomain()
        }

        val seedUser = User.register(
            email = Email.of(SEED_USER_EMAIL),
            nickname = Nickname.of(SEED_USER_NICKNAME),
            authProvider = AuthProvider.LOCAL,
            providerUserId = null,
            registeredAt = registeredAt
        )

        return userJpaRepository.save(UserJpaEntity.from(seedUser)).toDomain()
    }

    /** 이름을 정규화해 canonical 키워드를 찾고 없으면 원문을 displayName으로 만든다. */
    private fun findOrCreateKeyword(
        keywordJpaRepository: KeywordJpaRepository,
        name: String,
        createdAt: Instant
    ): Keyword {
        val canonicalKey = CanonicalKey.of(name)
        val existing = keywordJpaRepository.findByCanonicalKey(canonicalKey.value)
        if (existing != null) {
            return existing.toDomain()
        }

        val keyword = Keyword.create(displayName = KeywordName.of(name), createdAt = createdAt)

        return keywordJpaRepository.save(KeywordJpaEntity.from(keyword)).toDomain()
    }

    /** 시드 사용자의 구독을 찾고 없으면 시드 채널로 만든다. 비활성화돼 있으면 다시 활성화해 수집 대상에 올린다. */
    private fun findOrCreateSubscription(
        subscriptionJpaRepository: SubscriptionJpaRepository,
        seedUser: User,
        keyword: Keyword,
        registeredAt: Instant
    ): Subscription {
        val existing = subscriptionJpaRepository.findByUserIdAndKeywordId(seedUser.id.value, keyword.id.value)
        if (existing != null) {
            val subscription = existing.toDomain()
            return if (subscription.enabled) {
                subscription
            } else {
                subscriptionJpaRepository.save(SubscriptionJpaEntity.from(subscription.enable())).toDomain()
            }
        }

        val subscription = Subscription.create(
            userId = seedUser.id,
            keywordId = keyword.id,
            channels = SEED_CHANNELS,
            registeredAt = registeredAt
        )

        return subscriptionJpaRepository.save(SubscriptionJpaEntity.from(subscription)).toDomain()
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalSeedDataConfig::class.java)

        private const val SEED_USER_EMAIL = "collector-admin@kachi.local"
        private const val SEED_USER_NICKNAME = "collector-admin"
        private val SEED_KEYWORD_NAMES = listOf("TRUMP", "NVIDIA", "SPACE-X", "TESLA", "이란")
        private val SEED_CHANNELS = setOf(SubscriptionChannel.SLACK)
    }
}
