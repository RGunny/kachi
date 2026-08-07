package me.rgunny.kachi.user.adapter.inbound.web.oauth

import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.ResolveOAuthUserResult
import me.rgunny.kachi.user.application.port.inbound.auth.ResolveOAuthUserUseCase
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("OAuth2UserResolver")
class OAuth2UserResolverTest {

    @Test
    @DisplayName("OAuth2 attributes를 내부 인증 사용자로 변환한다")
    fun resolveOAuth2User() {
        val userId = UserId.newId()
        val useCase = FakeResolveOAuthUserUseCase(userId)
        val resolver = OAuth2UserResolver(useCase)
        val attributes = mapOf(
            "sub" to "google-123",
            "email" to "rgunny@kachi.com",
            "name" to "rgunny"
        )

        val authenticatedUser = resolver.resolve("google", attributes)

        assertEquals(userId, authenticatedUser.userId)
        assertEquals(userId.value.toString(), authenticatedUser.name)
        assertEquals(attributes, authenticatedUser.attributes)
        assertEquals(AuthProvider.GOOGLE, useCase.command?.authProvider)
        assertEquals("google-123", useCase.command?.providerUserId)
        assertEquals("rgunny@kachi.com", useCase.command?.email)
        assertEquals("rgunny", useCase.command?.nickname)
    }

    private class FakeResolveOAuthUserUseCase(
        private val userId: UserId
    ) : ResolveOAuthUserUseCase {
        var command: ResolveOAuthUserCommand? = null

        override fun resolve(command: ResolveOAuthUserCommand): ResolveOAuthUserResult {
            this.command = command

            return ResolveOAuthUserResult(userId)
        }
    }
}
