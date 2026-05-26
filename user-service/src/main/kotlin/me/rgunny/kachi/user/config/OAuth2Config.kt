package me.rgunny.kachi.user.config

import me.rgunny.kachi.user.adapter.`in`.web.oauth.CookieOAuth2AuthorizationRequestRepository
import me.rgunny.kachi.user.adapter.`in`.web.oauth.OAuth2AuthenticationSuccessHandler
import me.rgunny.kachi.user.application.port.`in`.IssueAuthTokensUseCase
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest

/**
 * OAuth2 브라우저 로그인 성공 처리와 stateless authorization request 저장소를 구성한다.
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties::class)
class OAuth2Config {

    @Bean
    fun oAuth2AuthenticationSuccessHandler(
        issueAuthTokensUseCase: IssueAuthTokensUseCase,
        oAuth2Properties: OAuth2Properties
    ): OAuth2AuthenticationSuccessHandler {
        return OAuth2AuthenticationSuccessHandler(
            issueAuthTokensUseCase = issueAuthTokensUseCase,
            successRedirectUri = oAuth2Properties.successRedirectUri
        )
    }

    @Bean
    fun oAuth2AuthorizationRequestRepository(
        jwtProperties: JwtProperties
    ): AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
        return CookieOAuth2AuthorizationRequestRepository(jwtProperties.secret)
    }
}
