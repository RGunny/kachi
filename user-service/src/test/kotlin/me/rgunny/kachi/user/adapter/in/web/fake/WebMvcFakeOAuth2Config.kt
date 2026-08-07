package me.rgunny.kachi.user.adapter.`in`.web.fake

import me.rgunny.kachi.user.adapter.`in`.web.oauth.CookieOAuth2AuthorizationRequestRepository
import me.rgunny.kachi.user.adapter.`in`.web.oauth.CustomOAuth2UserService
import me.rgunny.kachi.user.adapter.`in`.web.oauth.OAuth2AuthenticationSuccessHandler
import me.rgunny.kachi.user.adapter.`in`.web.oauth.OAuth2UserResolver
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensCommand
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensResult
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensUseCase
import me.rgunny.kachi.user.application.port.`in`.ResolveOAuthUserCommand
import me.rgunny.kachi.user.application.port.`in`.ResolveOAuthUserResult
import me.rgunny.kachi.user.application.port.`in`.ResolveOAuthUserUseCase
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import java.time.Duration

@TestConfiguration(proxyBeanMethods = false)
class WebMvcFakeOAuth2Config {

    @Bean
    fun customOAuth2UserService(): CustomOAuth2UserService {
        return CustomOAuth2UserService(OAuth2UserResolver(FakeResolveOAuthUserUseCase()))
    }

    @Bean
    fun oAuth2AuthenticationSuccessHandler(): OAuth2AuthenticationSuccessHandler {
        return OAuth2AuthenticationSuccessHandler(
            issueAuthTokensUseCase = FakeIssueAuthTokensUseCase(),
            successRedirectUri = "http://localhost:5173/oauth2/callback"
        )
    }

    @Bean
    fun oAuth2AuthorizationRequestRepository(): AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
        return CookieOAuth2AuthorizationRequestRepository("test-signing-secret")
    }

    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        return InMemoryClientRegistrationRepository(
            ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .scope("email", "profile")
                .clientName("Google")
                .build()
        )
    }

    private class FakeResolveOAuthUserUseCase : ResolveOAuthUserUseCase {
        override fun resolve(command: ResolveOAuthUserCommand): ResolveOAuthUserResult {
            return ResolveOAuthUserResult(UserId.newId())
        }
    }

    private class FakeIssueAuthTokensUseCase : IssueAuthTokensUseCase {
        override fun issue(command: IssueAuthTokensCommand): IssueAuthTokensResult {
            return IssueAuthTokensResult(
                accessToken = "access-token",
                accessTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofHours(3)).plus(Duration.ofMinutes(15)),
                refreshToken = "refresh-token",
                refreshTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofDays(14)).plus(Duration.ofHours(3))
            )
        }
    }
}
