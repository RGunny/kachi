package me.rgunny.kachi.user.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("OAuth2 client registration")
class OAuth2ClientRegistrationTest {

    @Test
    @DisplayName("Google, Kakao, Naver client registration 설정을 바인딩한다")
    fun bindOAuth2ClientRegistrations() {
        val properties = Binder.get(localYamlEnvironment())
            .bind("spring.security.oauth2.client", Bindable.of(OAuth2ClientProperties::class.java))
            .get()

        val google = assertNotNull(properties.registration["google"])
        assertEquals("local-google-client-id", google.clientId)
        assertEquals(setOf("email", "profile"), google.scope)

        val kakao = assertNotNull(properties.registration["kakao"])
        assertEquals("local-kakao-client-id", kakao.clientId)
        assertEquals("client_secret_post", kakao.clientAuthenticationMethod)
        assertEquals("authorization_code", kakao.authorizationGrantType)
        assertEquals("{baseUrl}/login/oauth2/code/{registrationId}", kakao.redirectUri)
        assertEquals(setOf("profile_nickname", "account_email"), kakao.scope)

        val kakaoProvider = assertNotNull(properties.provider["kakao"])
        assertEquals("https://kauth.kakao.com/oauth/authorize", kakaoProvider.authorizationUri)
        assertEquals("https://kauth.kakao.com/oauth/token", kakaoProvider.tokenUri)
        assertEquals("https://kapi.kakao.com/v2/user/me", kakaoProvider.userInfoUri)
        assertEquals("id", kakaoProvider.userNameAttribute)

        val naver = assertNotNull(properties.registration["naver"])
        assertEquals("local-naver-client-id", naver.clientId)
        assertEquals("authorization_code", naver.authorizationGrantType)
        assertEquals("{baseUrl}/login/oauth2/code/{registrationId}", naver.redirectUri)
        assertEquals(setOf("name", "email", "nickname"), naver.scope)
        assertEquals("Naver", naver.clientName)

        val naverProvider = assertNotNull(properties.provider["naver"])
        assertEquals("https://nid.naver.com/oauth2.0/authorize", naverProvider.authorizationUri)
        assertEquals("https://nid.naver.com/oauth2.0/token", naverProvider.tokenUri)
        assertEquals("https://openapi.naver.com/v1/nid/me", naverProvider.userInfoUri)
        assertEquals("response", naverProvider.userNameAttribute)
    }

    /**
     * 실제 기동과 같은 우선순위로 local profile이 base 설정을 덮어쓰도록 구성한다.
     */
    private fun localYamlEnvironment(): StandardEnvironment {
        val environment = StandardEnvironment()
        val loader = YamlPropertySourceLoader()
        listOf("application-local", "application").forEach { name ->
            loader.load(name, ClassPathResource("$name.yaml"))
                .forEach { environment.propertySources.addLast(it) }
        }

        return environment
    }
}
