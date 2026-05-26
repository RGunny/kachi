package me.rgunny.kachi.user.adapter.`in`.web.oauth

import me.rgunny.kachi.user.domain.AuthProvider

/**
 * Spring Security registrationId에 맞는 OAuth2UserInfo 구현체를 선택한다.
 */
object OAuth2UserInfoFactory {

    fun create(registrationId: String, attributes: Map<String, Any>): OAuth2UserInfo {
        return when (authProvider(registrationId)) {
            AuthProvider.GOOGLE -> GoogleOAuth2UserInfo(attributes)
            AuthProvider.KAKAO -> KakaoOAuth2UserInfo(attributes)
            AuthProvider.NAVER -> NaverOAuth2UserInfo(attributes)
            AuthProvider.LOCAL -> throw IllegalArgumentException("지원하지 않는 OAuth provider입니다: $registrationId")
        }
    }

    fun authProvider(registrationId: String): AuthProvider {
        return AuthProvider.entries.firstOrNull {
            it != AuthProvider.LOCAL && it.name.equals(registrationId, ignoreCase = true)
        } ?: throw IllegalArgumentException("지원하지 않는 OAuth provider입니다: $registrationId")
    }
}
