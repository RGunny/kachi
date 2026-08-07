package me.rgunny.kachi.user.adapter.inbound.web.oauth

class KakaoOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    private val kakaoAccount: Map<*, *>
        get() = attributes["kakao_account"] as Map<*, *>

    override val providerId: String
        get() = attributes["id"].toString()

    override val email: String
        get() = kakaoAccount["email"] as String

    override val nickname: String
        get() {
            val profile = kakaoAccount["profile"] as? Map<*, *>

            return profile?.get("nickname") as? String ?: email.substringBefore("@")
        }
}
