package me.rgunny.kachi.user.adapter.inbound.web.oauth

import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.inbound.auth.ResolveOAuthUserUseCase
import org.springframework.stereotype.Component

/**
 * Spring Security가 조회한 OAuth2 attributes를 내부 인증 사용자로 변환한다.
 */
@Component
class OAuth2UserResolver(
    private val resolveOAuthUserUseCase: ResolveOAuthUserUseCase
) {

    fun resolve(registrationId: String, attributes: Map<String, Any>): OAuth2AuthenticatedUser {
        val userInfo = OAuth2UserInfoFactory.create(registrationId, attributes)
        val result = resolveOAuthUserUseCase.resolve(
            ResolveOAuthUserCommand(
                authProvider = OAuth2UserInfoFactory.authProvider(registrationId),
                providerUserId = userInfo.providerId,
                email = userInfo.email,
                nickname = userInfo.nickname
            )
        )

        return OAuth2AuthenticatedUser(
            userId = result.userId,
            attributes = attributes
        )
    }
}
