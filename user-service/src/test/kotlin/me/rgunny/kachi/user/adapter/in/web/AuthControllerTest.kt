package me.rgunny.kachi.user.adapter.`in`.web

import me.rgunny.kachi.user.adapter.`in`.web.dto.RefreshTokenRequest
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenResult
import me.rgunny.kachi.user.application.port.`in`.RefreshTokenUseCase
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("AuthController")
class AuthControllerTest {

    @Test
    @DisplayName("refresh token으로 access/refresh token 갱신 요청을 처리한다")
    fun refreshToken() {
        val useCase = FakeRefreshTokenUseCase()
        val controller = AuthController(useCase)

        val response = controller.refresh(RefreshTokenRequest(refreshToken = "refresh-token"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body?.success)
        assertEquals(null, response.body?.error)
        assertEquals("refresh-token", useCase.command.refreshToken)
        assertEquals("access-token", response.body?.data?.accessToken)
        assertEquals("refresh-token", response.body?.data?.refreshToken)
    }

    private class FakeRefreshTokenUseCase : RefreshTokenUseCase {
        lateinit var command: RefreshTokenCommand

        override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
            this.command = command

            return RefreshTokenResult(
                accessToken = "access-token",
                accessTokenExpiresAt = Instant.parse("2026-05-20T00:15:00Z"),
                refreshToken = "refresh-token",
                refreshTokenExpiresAt = Instant.parse("2026-06-03T00:00:00Z")
            )
        }
    }
}
