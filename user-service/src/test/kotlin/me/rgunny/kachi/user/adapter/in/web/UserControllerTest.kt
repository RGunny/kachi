package me.rgunny.kachi.user.adapter.`in`.web

import me.rgunny.kachi.user.adapter.`in`.web.dto.RegisterUserRequest
import me.rgunny.kachi.user.application.port.`in`.RegisterUserCommand
import me.rgunny.kachi.user.application.port.`in`.RegisterUserResult
import me.rgunny.kachi.user.application.port.`in`.RegisterUserUseCase
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("UserController")
class UserControllerTest {
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("사용자 등록 요청을 처리하고 201 응답을 반환한다")
        fun registerUser() {
            val useCase = FakeRegisterUserUseCase()
            val controller = UserController(useCase)

            val response = controller.register(
                RegisterUserRequest(
                    email = "rgunny@kachi.com",
                    nickname = "rgunny",
                    authProvider = AuthProvider.GOOGLE
                )
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("rgunny@kachi.com", useCase.command.email)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals("rgunny", response.body?.data?.nickname)
            assertEquals("ACTIVE", response.body?.data?.status)
            assertEquals("USER", response.body?.data?.role)
            assertEquals("GOOGLE", response.body?.data?.authProvider)
            assertEquals(registeredAt, response.body?.data?.registeredAt)
        }
    }

    private inner class FakeRegisterUserUseCase : RegisterUserUseCase {
        lateinit var command: RegisterUserCommand

        override fun register(command: RegisterUserCommand): RegisterUserResult {
            this.command = command

            return RegisterUserResult(
                id = UserId.of(UUID.randomUUID()),
                email = command.email,
                nickname = command.nickname,
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                authProvider = command.authProvider,
                registeredAt = registeredAt
            )
        }
    }
}
