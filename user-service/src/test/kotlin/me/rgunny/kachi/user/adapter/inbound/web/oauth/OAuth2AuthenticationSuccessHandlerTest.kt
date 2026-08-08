package me.rgunny.kachi.user.adapter.inbound.web.oauth

import me.rgunny.kachi.user.application.port.inbound.auth.model.IssueAuthTokensCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.IssueAuthTokensResult
import me.rgunny.kachi.user.application.port.inbound.auth.IssueAuthTokensUseCase
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import java.time.Duration
import kotlin.test.assertEquals

@DisplayName("OAuth2AuthenticationSuccessHandler")
class OAuth2AuthenticationSuccessHandlerTest {

    @Test
    @DisplayName("OAuth2 인증 성공 사용자의 토큰을 발급하고 프론트 콜백으로 리다이렉트한다")
    fun redirectWithIssuedTokens() {
        val userId = UserId.newId()
        val useCase = FakeIssueAuthTokensUseCase()
        val handler = OAuth2AuthenticationSuccessHandler(
            issueAuthTokensUseCase = useCase,
            successRedirectUri = "http://localhost:5173/oauth2/callback"
        )
        val authentication = TestingAuthenticationToken(
            OAuth2AuthenticatedUser(userId, mapOf("sub" to "google-123")),
            "oauth2"
        )
        val response = MockHttpServletResponse()

        handler.onAuthenticationSuccess(
            MockHttpServletRequest(),
            response,
            authentication
        )

        assertEquals(userId, useCase.command?.userId)
        assertEquals(
            "http://localhost:5173/oauth2/callback#accessToken=access-token&refreshToken=refresh-token",
            response.redirectedUrl
        )
    }

    private class FakeIssueAuthTokensUseCase : IssueAuthTokensUseCase {
        var command: IssueAuthTokensCommand? = null

        override fun issue(command: IssueAuthTokensCommand): IssueAuthTokensResult {
            this.command = command

            return IssueAuthTokensResult(
                accessToken = "access-token",
                accessTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(15)),
                refreshToken = "refresh-token",
                refreshTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofDays(14)).plus(Duration.ofHours(3))
            )
        }
    }
}
