package me.rgunny.kachi.user.adapter.inbound.web

import me.rgunny.kachi.user.adapter.inbound.web.dto.LogoutRequest
import me.rgunny.kachi.user.adapter.inbound.web.dto.RefreshTokenRequest
import me.rgunny.kachi.user.application.port.inbound.auth.model.LogoutCommand
import me.rgunny.kachi.user.application.port.inbound.auth.LogoutUseCase
import me.rgunny.kachi.user.application.port.inbound.auth.model.RefreshTokenCommand
import me.rgunny.kachi.user.application.port.inbound.auth.model.RefreshTokenResult
import me.rgunny.kachi.user.application.port.inbound.auth.RefreshTokenUseCase
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import kotlin.test.assertEquals

@DisplayName("AuthController")
class AuthControllerTest {

    @Test
    @DisplayName("refresh token으로 access/refresh token 갱신 요청을 처리한다")
    fun refreshToken() {
        val useCase = FakeRefreshTokenUseCase()
        val controller = AuthController(
            refreshTokenUseCase = useCase,
            logoutUseCase = FakeLogoutUseCase()
        )

        val response = controller.refresh(RefreshTokenRequest(refreshToken = "refresh-token"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body?.success)
        assertEquals(null, response.body?.error)
        assertEquals("refresh-token", useCase.command.refreshToken)
        assertEquals("access-token", response.body?.data?.accessToken)
        assertEquals("refresh-token", response.body?.data?.refreshToken)
    }

    @Test
    @DisplayName("refresh token 폐기 요청을 처리한다")
    fun logout() {
        val useCase = FakeLogoutUseCase()
        val controller = AuthController(
            refreshTokenUseCase = FakeRefreshTokenUseCase(),
            logoutUseCase = useCase
        )

        val response = controller.logout(LogoutRequest(refreshToken = "refresh-token"))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body?.success)
        assertEquals(null, response.body?.error)
        assertEquals(null, response.body?.data)
        assertEquals("refresh-token", useCase.command.refreshToken)
    }

    private class FakeRefreshTokenUseCase : RefreshTokenUseCase {
        lateinit var command: RefreshTokenCommand

        override fun refresh(command: RefreshTokenCommand): RefreshTokenResult {
            this.command = command

            return RefreshTokenResult(
                accessToken = "access-token",
                accessTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofMinutes(15)),
                refreshToken = "refresh-token",
                refreshTokenExpiresAt = UserTestFixture.NOW.plus(Duration.ofDays(14))
            )
        }
    }

    private class FakeLogoutUseCase : LogoutUseCase {
        lateinit var command: LogoutCommand

        override fun logout(command: LogoutCommand) {
            this.command = command
        }
    }
}
