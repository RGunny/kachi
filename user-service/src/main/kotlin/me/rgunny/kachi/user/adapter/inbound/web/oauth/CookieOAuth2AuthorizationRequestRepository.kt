package me.rgunny.kachi.user.adapter.inbound.web.oauth

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * JWT 기반 stateless API에서 OAuth2 authorization request를 HttpSession 대신 서명된 쿠키에 보관한다.
 *
 * OAuth2 login은 provider로 이동하기 전에 생성한 authorization request를 callback 시점까지 보관해야 한다.
 * Spring Security 기본 구현은 HttpSession을 사용하지만, 이 서비스는 stateless API를 유지하기 위해 쿠키를 사용한다.
 * 쿠키 값은 클라이언트가 보관하므로 HMAC 서명으로 변조 여부를 검증한다.
 */
class CookieOAuth2AuthorizationRequestRepository(
    private val signingSecret: String
) : AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    override fun loadAuthorizationRequest(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        // callback 요청 쿠키에서 authorization request 값을 찾는다.
        return request.cookies
            ?.firstOrNull { it.name == AUTHORIZATION_REQUEST_COOKIE_NAME }
            ?.value
            // 쿠키 값이 서버 서명과 일치하는 경우에만 역직렬화한다.
            ?.takeIf(::isValidCookieValue)
            ?.substringBefore(COOKIE_VALUE_SEPARATOR)
            ?.let(::deserialize)
    }

    override fun saveAuthorizationRequest(
        authorizationRequest: OAuth2AuthorizationRequest?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ) {
        // 1. 저장할 값이 없으면 기존 authorization request 쿠키를 만료한다.
        if (authorizationRequest == null) {
            expireCookie(request, response)
            return
        }

        // 2. provider 이동 전 authorization request를 직렬화하고 서명해서 쿠키에 저장한다.
        response.addCookie(
            Cookie(AUTHORIZATION_REQUEST_COOKIE_NAME, signedCookieValue(serialize(authorizationRequest))).apply {
                path = COOKIE_PATH
                isHttpOnly = true
                secure = request.isSecure
                maxAge = AUTHORIZATION_REQUEST_COOKIE_MAX_AGE_SECONDS
            }
        )
    }

    override fun removeAuthorizationRequest(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): OAuth2AuthorizationRequest? {
        // 1. callback 검증에 사용할 기존 authorization request를 먼저 읽는다.
        val authorizationRequest = loadAuthorizationRequest(request)

        // 2. 한 번 사용한 authorization request는 재사용되지 않도록 쿠키를 만료한다.
        expireCookie(request, response)

        return authorizationRequest
    }

    private fun expireCookie(request: HttpServletRequest, response: HttpServletResponse) {
        response.addCookie(
            Cookie(AUTHORIZATION_REQUEST_COOKIE_NAME, "").apply {
                path = COOKIE_PATH
                isHttpOnly = true
                secure = request.isSecure
                maxAge = 0
            }
        )
    }

    private fun serialize(authorizationRequest: OAuth2AuthorizationRequest): String {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(authorizationRequest) }
        return Base64.getUrlEncoder().encodeToString(bytes.toByteArray())
    }

    private fun deserialize(value: String): OAuth2AuthorizationRequest {
        val bytes = Base64.getUrlDecoder().decode(value)
        return ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as OAuth2AuthorizationRequest
        }
    }

    private fun signedCookieValue(payload: String): String {
        return "$payload$COOKIE_VALUE_SEPARATOR${signature(payload)}"
    }

    private fun isValidCookieValue(value: String): Boolean {
        val payload = value.substringBefore(COOKIE_VALUE_SEPARATOR, "")
        val actualSignature = value.substringAfter(COOKIE_VALUE_SEPARATOR, "")

        if (payload.isBlank() || actualSignature.isBlank()) {
            return false
        }

        return MessageDigest.isEqual(
            signature(payload).toByteArray(),
            actualSignature.toByteArray()
        )
    }

    private fun signature(payload: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(signingSecret.toByteArray(), HMAC_ALGORITHM))
        return Base64.getUrlEncoder().encodeToString(mac.doFinal(payload.toByteArray()))
    }

    companion object {
        private const val AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_authorization_request"
        private const val AUTHORIZATION_REQUEST_COOKIE_MAX_AGE_SECONDS = 180
        private const val COOKIE_VALUE_SEPARATOR = "."
        private const val COOKIE_PATH = "/"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
