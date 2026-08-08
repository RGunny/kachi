package me.rgunny.kachi.user.adapter.inbound.web.oauth

class NaverOAuth2UserInfo(
    private val attributes: Map<String, Any>
) : OAuth2UserInfo {

    private val response: Map<*, *>
        get() = attributes["response"] as Map<*, *>

    override val providerId: String
        get() = response["id"] as String

    override val email: String
        get() = response["email"] as String

    override val nickname: String
        get() = response["nickname"] as? String
            ?: response["name"] as? String
            ?: email.substringBefore("@")
}
