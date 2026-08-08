package me.rgunny.kachi.user.adapter.inbound.web.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.rgunny.kachi.user.application.port.inbound.auth.model.IssueAuthTokensCommand
import me.rgunny.kachi.user.application.port.inbound.auth.IssueAuthTokensUseCase
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.web.util.UriComponentsBuilder

/**
 * OAuth2 로그인 성공 사용자를 JWT 기반 API 인증 토큰으로 전환하고 프론트 콜백으로 이동시킨다.
 */
class OAuth2AuthenticationSuccessHandler(
    private val issueAuthTokensUseCase: IssueAuthTokensUseCase,
    private val successRedirectUri: String
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        // 1. Spring Security 인증 결과에서 내부 사용자 식별자를 꺼낸다.
        val authenticatedUser = authentication.principal as OAuth2AuthenticatedUser

        // 2. 일반 로그인과 동일한 유스케이스로 access/refresh token을 발급한다.
        val tokens = issueAuthTokensUseCase.issue(IssueAuthTokensCommand(authenticatedUser.userId))

        // 3. 프론트 OAuth2 콜백 주소로 토큰을 전달한다.
        response.sendRedirect(
            UriComponentsBuilder.fromUriString(successRedirectUri)
                .fragment(tokenFragment(tokens.accessToken, tokens.refreshToken))
                .build()
                .toUriString()
        )
    }

    private fun tokenFragment(accessToken: String, refreshToken: String): String {
        return UriComponentsBuilder.newInstance()
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .query.orEmpty()
    }
}
