package me.rgunny.kachi.user.adapter.inbound.web.oauth

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

/**
 * Spring Security OAuth2 Client가 provider에서 사용자 정보를 조회한 뒤 내부 사용자로 변환한다.
 */
@Service
class CustomOAuth2UserService(
    private val oAuth2UserResolver: OAuth2UserResolver
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        // 1. Spring Security 기본 구현으로 provider user info endpoint를 호출한다.
        val oAuth2User = super.loadUser(userRequest)

        // 2. provider별 attributes를 내부 사용자 식별 기준으로 변환한다.
        return oAuth2UserResolver.resolve(
            registrationId = userRequest.clientRegistration.registrationId,
            attributes = oAuth2User.attributes
        )
    }
}
