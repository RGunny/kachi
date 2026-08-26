package me.rgunny.kachi.user.fixture

import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.Subscription
import me.rgunny.kachi.user.domain.SubscriptionChannel
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * user-service 테스트가 공유하는 고정 시간과 도메인 객체 팩토리.
 *
 * 가입·로그인·토큰 만료의 선후 관계를 테스트마다 다시 세우면 기준 시각이 조금씩 어긋난다.
 * 시나리오 고유 시각은 이 상수의 상대값([NOW].plus 등)으로 표현한다.
 * 팩토리는 시나리오와 무관한 필드를 기본값으로 채워 테스트가 관심 있는 값만 드러내게 한다.
 */
object UserTestFixture {
    val NOW: Instant = Instant.parse("2026-05-20T00:00:00Z")
    val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)

    /** 이메일·닉네임·provider가 시나리오와 무관한 사용자. [status]만 바꿔 활성·비활성 분기를 만든다. */
    fun user(id: UserId = UserId.newId(), status: UserStatus = UserStatus.ACTIVE): User {
        return User.restore(
            id = id,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = status,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = NOW,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }

    /** 원문 [name]으로 만든 canonical 키워드. canonicalKey는 정규화 결과다. */
    fun keyword(name: String, createdAt: Instant = NOW): Keyword {
        return Keyword.create(displayName = KeywordName.of(name), createdAt = createdAt)
    }

    /** 활성 구독. 채널을 지정하지 않으면 SLACK 하나다. */
    fun subscription(
        userId: UserId,
        keywordId: KeywordId,
        channels: Set<SubscriptionChannel> = setOf(SubscriptionChannel.SLACK),
        registeredAt: Instant = NOW
    ): Subscription {
        return Subscription.create(
            userId = userId,
            keywordId = keywordId,
            channels = channels,
            registeredAt = registeredAt
        )
    }
}
