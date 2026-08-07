package me.rgunny.kachi.user.adapter.inbound.web.oauth

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@DisplayName("OAuth2UserInfo")
class OAuth2UserInfoTest {

    @Nested
    @DisplayName("GoogleOAuth2UserInfo")
    inner class Google {

        @Test
        @DisplayName("Google 응답에서 provider id, email, nickname을 읽는다")
        fun readGoogleAttributes() {
            val userInfo = GoogleOAuth2UserInfo(
                mapOf(
                    "sub" to "google-123",
                    "email" to "rgunny@kachi.com",
                    "name" to "rgunny"
                )
            )

            assertEquals("google-123", userInfo.providerId)
            assertEquals("rgunny@kachi.com", userInfo.email)
            assertEquals("rgunny", userInfo.nickname)
        }

        @Test
        @DisplayName("Google nickname이 없으면 email 앞부분을 사용한다")
        fun fallbackGoogleNickname() {
            val userInfo = GoogleOAuth2UserInfo(
                mapOf(
                    "sub" to "google-123",
                    "email" to "rgunny@kachi.com"
                )
            )

            assertEquals("rgunny", userInfo.nickname)
        }
    }

    @Nested
    @DisplayName("KakaoOAuth2UserInfo")
    inner class Kakao {

        @Test
        @DisplayName("Kakao 중첩 응답에서 provider id, email, nickname을 읽는다")
        fun readKakaoAttributes() {
            val userInfo = KakaoOAuth2UserInfo(
                mapOf(
                    "id" to 1234567890L,
                    "kakao_account" to mapOf(
                        "email" to "rgunny@kachi.com",
                        "profile" to mapOf("nickname" to "rgunny")
                    )
                )
            )

            assertEquals("1234567890", userInfo.providerId)
            assertEquals("rgunny@kachi.com", userInfo.email)
            assertEquals("rgunny", userInfo.nickname)
        }

        @Test
        @DisplayName("Kakao profile nickname이 없으면 email 앞부분을 사용한다")
        fun fallbackKakaoNickname() {
            val userInfo = KakaoOAuth2UserInfo(
                mapOf(
                    "id" to 1234567890L,
                    "kakao_account" to mapOf("email" to "rgunny@kachi.com")
                )
            )

            assertEquals("rgunny", userInfo.nickname)
        }
    }

    @Nested
    @DisplayName("NaverOAuth2UserInfo")
    inner class Naver {

        @Test
        @DisplayName("Naver 중첩 응답에서 provider id, email, nickname을 읽는다")
        fun readNaverAttributes() {
            val userInfo = NaverOAuth2UserInfo(
                mapOf(
                    "response" to mapOf(
                        "id" to "naver-123",
                        "email" to "rgunny@kachi.com",
                        "nickname" to "rgunny"
                    )
                )
            )

            assertEquals("naver-123", userInfo.providerId)
            assertEquals("rgunny@kachi.com", userInfo.email)
            assertEquals("rgunny", userInfo.nickname)
        }

        @Test
        @DisplayName("Naver nickname이 없으면 name을 사용한다")
        fun fallbackNaverNicknameToName() {
            val userInfo = NaverOAuth2UserInfo(
                mapOf(
                    "response" to mapOf(
                        "id" to "naver-123",
                        "email" to "rgunny@kachi.com",
                        "name" to "gunny"
                    )
                )
            )

            assertEquals("gunny", userInfo.nickname)
        }

        @Test
        @DisplayName("Naver nickname과 name이 없으면 email 앞부분을 사용한다")
        fun fallbackNaverNicknameToEmail() {
            val userInfo = NaverOAuth2UserInfo(
                mapOf(
                    "response" to mapOf(
                        "id" to "naver-123",
                        "email" to "rgunny@kachi.com"
                    )
                )
            )

            assertEquals("rgunny", userInfo.nickname)
        }
    }

    @Nested
    @DisplayName("OAuth2UserInfoFactory")
    inner class Factory {

        @Test
        @DisplayName("registrationId로 provider별 UserInfo 구현체를 선택한다")
        fun createUserInfo() {
            assertIs<GoogleOAuth2UserInfo>(OAuth2UserInfoFactory.create("google", googleAttributes()))
            assertIs<KakaoOAuth2UserInfo>(OAuth2UserInfoFactory.create("kakao", kakaoAttributes()))
            assertIs<NaverOAuth2UserInfo>(OAuth2UserInfoFactory.create("naver", naverAttributes()))
        }

        @Test
        @DisplayName("지원하지 않는 registrationId는 실패한다")
        fun rejectUnsupportedRegistrationId() {
            assertFailsWith<IllegalArgumentException> {
                OAuth2UserInfoFactory.create("github", emptyMap())
            }
        }
    }

    private fun googleAttributes(): Map<String, Any> {
        return mapOf(
            "sub" to "google-123",
            "email" to "rgunny@kachi.com",
            "name" to "rgunny"
        )
    }

    private fun kakaoAttributes(): Map<String, Any> {
        return mapOf(
            "id" to 1234567890L,
            "kakao_account" to mapOf(
                "email" to "rgunny@kachi.com",
                "profile" to mapOf("nickname" to "rgunny")
            )
        )
    }

    private fun naverAttributes(): Map<String, Any> {
        return mapOf(
            "response" to mapOf(
                "id" to "naver-123",
                "email" to "rgunny@kachi.com",
                "nickname" to "rgunny"
            )
        )
    }
}
