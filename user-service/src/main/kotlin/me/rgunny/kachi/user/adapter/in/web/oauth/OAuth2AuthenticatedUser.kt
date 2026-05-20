package me.rgunny.kachi.user.adapter.`in`.web.oauth

import me.rgunny.kachi.user.domain.UserId
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class OAuth2AuthenticatedUser(
    val userId: UserId,
    private val attributes: Map<String, Any>
) : OAuth2User {

    override fun getName(): String {
        return userId.value.toString()
    }

    override fun getAttributes(): Map<String, Any> {
        return attributes
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return listOf(SimpleGrantedAuthority("ROLE_USER"))
    }
}
