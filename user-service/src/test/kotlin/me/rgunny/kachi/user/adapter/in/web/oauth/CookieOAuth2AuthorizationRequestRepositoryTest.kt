package me.rgunny.kachi.user.adapter.`in`.web.oauth

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("CookieOAuth2AuthorizationRequestRepository")
class CookieOAuth2AuthorizationRequestRepositoryTest {

    @Test
    @DisplayName("OAuth2 authorization request를 쿠키에 저장하고 다시 조회한다")
    fun saveAndLoadAuthorizationRequest() {
        val repository = CookieOAuth2AuthorizationRequestRepository(SIGNING_SECRET)
        val response = MockHttpServletResponse()
        val authorizationRequest = authorizationRequest()

        repository.saveAuthorizationRequest(
            authorizationRequest,
            MockHttpServletRequest(),
            response
        )

        val cookie = assertNotNull(response.getCookie("oauth2_authorization_request"))
        val request = MockHttpServletRequest().apply {
            setCookies(cookie)
        }
        val loaded = repository.loadAuthorizationRequest(request)

        assertEquals(authorizationRequest.authorizationUri, loaded?.authorizationUri)
        assertEquals(authorizationRequest.clientId, loaded?.clientId)
        assertEquals(authorizationRequest.redirectUri, loaded?.redirectUri)
        assertEquals(authorizationRequest.state, loaded?.state)
        assertEquals(authorizationRequest.scopes, loaded?.scopes)
    }

    @Test
    @DisplayName("OAuth2 authorization request를 제거할 때 기존 값을 반환하고 쿠키를 만료한다")
    fun removeAuthorizationRequest() {
        val repository = CookieOAuth2AuthorizationRequestRepository(SIGNING_SECRET)
        val saveResponse = MockHttpServletResponse()
        val authorizationRequest = authorizationRequest()
        repository.saveAuthorizationRequest(authorizationRequest, MockHttpServletRequest(), saveResponse)
        val cookie = assertNotNull(saveResponse.getCookie("oauth2_authorization_request"))
        val request = MockHttpServletRequest().apply {
            setCookies(cookie)
        }
        val removeResponse = MockHttpServletResponse()

        val removed = repository.removeAuthorizationRequest(request, removeResponse)

        assertEquals(authorizationRequest.state, removed?.state)
        assertEquals(0, removeResponse.getCookie("oauth2_authorization_request")?.maxAge)
    }

    @Test
    @DisplayName("서명이 맞지 않는 OAuth2 authorization request 쿠키는 사용하지 않는다")
    fun rejectTamperedCookie() {
        val repository = CookieOAuth2AuthorizationRequestRepository(SIGNING_SECRET)
        val saveResponse = MockHttpServletResponse()
        repository.saveAuthorizationRequest(authorizationRequest(), MockHttpServletRequest(), saveResponse)
        val cookie = assertNotNull(saveResponse.getCookie("oauth2_authorization_request"))
        cookie.value = "${cookie.value.substringBefore(".")}.tampered-signature"
        val request = MockHttpServletRequest().apply {
            setCookies(cookie)
        }

        val loaded = repository.loadAuthorizationRequest(request)

        assertEquals(null, loaded)
    }

    private fun authorizationRequest(): OAuth2AuthorizationRequest {
        return OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .clientId("client-id")
            .redirectUri("http://localhost/login/oauth2/code/google")
            .scopes(setOf("email", "profile"))
            .state("state")
            .build()
    }

    companion object {
        private const val SIGNING_SECRET = "test-signing-secret"
    }
}
