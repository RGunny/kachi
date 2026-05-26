package me.rgunny.kachi.user.domain

import java.time.Instant

class User private constructor(
    val id: UserId,
    val email: Email,
    val nickname: Nickname,
    val status: UserStatus = UserStatus.ACTIVE,
    val role: UserRole = UserRole.USER,
    val authProvider: AuthProvider,
    val providerUserId: ProviderUserId?,
    val registeredAt: Instant,
    val lastLoginAt: Instant?,
    val deactivatedAt: Instant?
) {
    companion object {

        fun register(
            email: Email,
            nickname: Nickname,
            authProvider: AuthProvider,
            providerUserId: ProviderUserId?,
            registeredAt: Instant
        ): User {
            require(authProvider != AuthProvider.LOCAL || providerUserId == null) {
                "LOCAL 사용자는 OAuth provider 사용자 ID를 가질 수 없습니다"
            }
            require(authProvider == AuthProvider.LOCAL || providerUserId != null) {
                "OAuth 사용자는 provider 사용자 ID가 필요합니다"
            }

            return User(
                id = UserId.newId(),
                email = email,
                nickname = nickname,
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                authProvider = authProvider,
                providerUserId = providerUserId,
                registeredAt = registeredAt,
                deactivatedAt = null,
                lastLoginAt = null,
            )
        }

        fun restore(
            id: UserId,
            email: Email,
            nickname: Nickname,
            status: UserStatus,
            role: UserRole,
            authProvider: AuthProvider,
            providerUserId: ProviderUserId?,
            registeredAt: Instant,
            lastLoginAt: Instant?,
            deactivatedAt: Instant?
        ): User {
            return User(
                id = id,
                email = email,
                nickname = nickname,
                status = status,
                role = role,
                authProvider = authProvider,
                providerUserId = providerUserId,
                registeredAt = registeredAt,
                lastLoginAt = lastLoginAt,
                deactivatedAt = deactivatedAt
            )
        }
    }

    fun activate(): User {
        require(status != UserStatus.DELETED) { "탈퇴한 사용자는 활성화할 수 없습니다" }

        return User(
            id = id,
            email = email,
            nickname = nickname,
            status = UserStatus.ACTIVE,
            role = role,
            authProvider = authProvider,
            providerUserId = providerUserId,
            registeredAt = registeredAt,
            lastLoginAt = lastLoginAt,
            deactivatedAt = null
        )
    }

    fun deactivate(deactivatedAt: Instant): User {
        require(status != UserStatus.DELETED) { "이미 탈퇴한 사용자입니다" }

        return User(
            id = id,
            email = email,
            nickname = nickname,
            status = UserStatus.DELETED,
            role = role,
            authProvider = authProvider,
            providerUserId = providerUserId,
            registeredAt = registeredAt,
            lastLoginAt = lastLoginAt,
            deactivatedAt = deactivatedAt
        )
    }

    fun recordLogin(loggedInAt: Instant): User {
        require(status == UserStatus.ACTIVE) { "활성 사용자만 로그인 시각을 기록할 수 있습니다" }

        return User(
            id = id,
            email = email,
            nickname = nickname,
            status = status,
            role = role,
            authProvider = authProvider,
            providerUserId = providerUserId,
            registeredAt = registeredAt,
            lastLoginAt = loggedInAt,
            deactivatedAt = deactivatedAt
        )
    }
}
