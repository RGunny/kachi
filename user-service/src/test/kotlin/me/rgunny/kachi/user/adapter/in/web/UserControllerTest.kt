package me.rgunny.kachi.user.adapter.`in`.web

import me.rgunny.kachi.user.adapter.`in`.web.dto.RegisterUserRequest
import me.rgunny.kachi.user.adapter.`in`.web.security.AuthenticatedUser
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserCommand
import me.rgunny.kachi.user.application.port.`in`.DeactivateUserUseCase
import me.rgunny.kachi.user.application.port.`in`.GetUserQuery
import me.rgunny.kachi.user.application.port.`in`.GetUserResult
import me.rgunny.kachi.user.application.port.`in`.GetUserUseCase
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
    private val userId = UserId.of(UUID.randomUUID())
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("getMe()")
    inner class GetMe {

        @Test
        @DisplayName("인증 사용자 기준 내 정보를 조회한다")
        fun getMe() {
            val registerUseCase = FakeRegisterUserUseCase()
            val getUseCase = FakeGetUserUseCase()
            val controller = UserController(registerUseCase, getUseCase, FakeDeactivateUserUseCase())
            val authenticatedUser = AuthenticatedUser(
                userId = userId,
                role = UserRole.USER
            )

            val response = controller.getMe(authenticatedUser)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals(userId, getUseCase.query.userId)
            assertEquals(userId.value.toString(), response.body?.data?.id)
            assertEquals("rgunny@kachi.com", response.body?.data?.email)
            assertEquals("rgunny", response.body?.data?.nickname)
        }
    }

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("사용자 등록 요청을 처리하고 201 응답을 반환한다")
        fun registerUser() {
            val registerUseCase = FakeRegisterUserUseCase()
            val getUseCase = FakeGetUserUseCase()
            val controller = UserController(registerUseCase, getUseCase, FakeDeactivateUserUseCase())

            val response = controller.register(
                RegisterUserRequest(
                    email = "rgunny@kachi.com",
                    nickname = "rgunny",
                    authProvider = AuthProvider.GOOGLE
                )
            )

            assertEquals(HttpStatus.CREATED, response.statusCode)
            assertEquals("rgunny@kachi.com", registerUseCase.command.email)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals("rgunny", response.body?.data?.nickname)
            assertEquals("ACTIVE", response.body?.data?.status)
            assertEquals("USER", response.body?.data?.role)
            assertEquals("GOOGLE", response.body?.data?.authProvider)
            assertEquals(registeredAt, response.body?.data?.registeredAt)
        }
    }

    @Nested
    @DisplayName("deactivateMe()")
    inner class DeactivateMe {

        @Test
        @DisplayName("인증 사용자 기준 탈퇴 요청을 처리한다")
        fun deactivateMe() {
            val deactivateUseCase = FakeDeactivateUserUseCase()
            val controller = UserController(
                registerUserUseCase = FakeRegisterUserUseCase(),
                getUserUseCase = FakeGetUserUseCase(),
                deactivateUserUseCase = deactivateUseCase
            )
            val authenticatedUser = AuthenticatedUser(
                userId = userId,
                role = UserRole.USER
            )

            val response = controller.deactivateMe(authenticatedUser)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertEquals(true, response.body?.success)
            assertEquals(null, response.body?.error)
            assertEquals(null, response.body?.data)
            assertEquals(userId, deactivateUseCase.command.userId)
        }
    }

    private inner class FakeGetUserUseCase : GetUserUseCase {
        lateinit var query: GetUserQuery

        override fun get(query: GetUserQuery): GetUserResult {
            this.query = query

            return GetUserResult(
                id = query.userId,
                email = "rgunny@kachi.com",
                nickname = "rgunny",
                status = UserStatus.ACTIVE,
                role = UserRole.USER,
                authProvider = AuthProvider.GOOGLE,
                registeredAt = registeredAt
            )
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

    private class FakeDeactivateUserUseCase : DeactivateUserUseCase {
        lateinit var command: DeactivateUserCommand

        override fun deactivate(command: DeactivateUserCommand) {
            this.command = command
        }
    }
}
