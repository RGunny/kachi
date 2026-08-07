package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateEmailException
import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserResult
import me.rgunny.kachi.user.application.port.inbound.auth.ResolveOAuthUserUseCase
import me.rgunny.kachi.user.application.port.outbound.user.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

/**
 * OAuth2 provider 사용자 정보로 기존 User를 찾고, 없으면 신규 User를 등록한다.
 */
@Service
class OAuthUserService(
    private val userPersistencePort: UserPersistencePort,
    private val clock: Clock
) : ResolveOAuthUserUseCase {

    override fun resolve(command: ResolveOAuthUserCommand): ResolveOAuthUserResult {
        // 1. OAuth 로그인에는 외부 provider만 허용한다.
        require(command.authProvider != AuthProvider.LOCAL) {
            "OAuth 로그인에는 LOCAL provider를 사용할 수 없습니다"
        }

        // 2. provider가 보장하는 사용자 ID로 기존 사용자를 찾는다.
        val providerUserId = ProviderUserId.of(command.providerUserId)
        val existingUser = userPersistencePort.findByAuthProviderAndProviderUserId(
            authProvider = command.authProvider,
            providerUserId = providerUserId
        )

        if (existingUser != null) {
            return ResolveOAuthUserResult(existingUser.id)
        }

        // 3. 신규 등록 전 email 중복 정책을 확인한다.
        val email = Email.of(command.email)
        if (userPersistencePort.existsByEmail(email)) {
            throw DuplicateEmailException(email)
        }

        // 4. provider 사용자 정보를 내부 User로 등록한다.
        val user = User.register(
            email = email,
            nickname = Nickname.of(command.nickname),
            authProvider = command.authProvider,
            providerUserId = providerUserId,
            registeredAt = Instant.now(clock)
        )

        return ResolveOAuthUserResult(userPersistencePort.save(user).id)
    }
}
