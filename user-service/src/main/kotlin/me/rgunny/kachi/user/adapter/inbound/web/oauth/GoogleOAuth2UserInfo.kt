package me.rgunny.kachi.user.adapter.inbound.web.oauth

class GoogleOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    override val providerId: String
        get() = attributes["sub"] as String

    override val email: String
        get() = attributes["email"] as String

    override val nickname: String
        get() = attributes["name"] as? String ?: email.substringBefore("@")
}
