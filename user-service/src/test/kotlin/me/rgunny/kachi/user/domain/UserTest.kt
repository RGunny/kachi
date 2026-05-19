package me.rgunny.kachi.user.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("User")
class UserTest {

    private val email = Email.of("rgunny@kachi.com")
    private val nickname = Nickname.of("rgunny")
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("사용자를 등록하면 활성 상태와 기본 권한을 가진다")
        fun registerCreatesActiveUserWithDefaultRole() {
            val user = User.register(
                email = email,
                nickname = nickname,
                authProvider = AuthProvider.GOOGLE,
                registeredAt = registeredAt
            )

            assertNotNull(user.id.value)
            assertEquals(email, user.email)
            assertEquals(nickname, user.nickname)
            assertEquals(UserStatus.ACTIVE, user.status)
            assertEquals(UserRole.USER, user.role)
            assertEquals(AuthProvider.GOOGLE, user.authProvider)
            assertEquals(registeredAt, user.registeredAt)
            assertNull(user.lastLoginAt)
            assertNull(user.deactivatedAt)
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("사용자를 복원하면 저장된 상태를 그대로 가진다")
        fun restoreRebuildsUserFromPersistedState() {
            val id = UserId.of(UUID.randomUUID())
            val lastLoginAt = Instant.parse("2026-05-20T01:00:00Z")
            val deactivatedAt = Instant.parse("2026-05-20T02:00:00Z")

            val user = User.restore(
                id = id,
                email = email,
                nickname = nickname,
                status = UserStatus.DELETED,
                role = UserRole.ADMIN,
                authProvider = AuthProvider.LOCAL,
                registeredAt = registeredAt,
                lastLoginAt = lastLoginAt,
                deactivatedAt = deactivatedAt
            )

            assertEquals(id, user.id)
            assertEquals(UserStatus.DELETED, user.status)
            assertEquals(UserRole.ADMIN, user.role)
            assertEquals(AuthProvider.LOCAL, user.authProvider)
            assertEquals(registeredAt, user.registeredAt)
            assertEquals(lastLoginAt, user.lastLoginAt)
            assertEquals(deactivatedAt, user.deactivatedAt)
        }
    }

    @Nested
    @DisplayName("activate()")
    inner class Activate {

        @Test
        @DisplayName("휴면 사용자를 활성화하면 활성 상태가 되고 탈퇴 시간이 제거된다")
        fun activateInactiveUser() {
            val inactiveUser = User.restore(
                id = UserId.of(UUID.randomUUID()),
                email = email,
                nickname = nickname,
                status = UserStatus.INACTIVE,
                role = UserRole.USER,
                authProvider = AuthProvider.NAVER,
                registeredAt = registeredAt,
                lastLoginAt = Instant.parse("2026-05-20T01:00:00Z"),
                deactivatedAt = Instant.parse("2026-05-20T02:00:00Z")
            )

            val activatedUser = inactiveUser.activate()

            assertEquals(UserStatus.ACTIVE, activatedUser.status)
            assertEquals(inactiveUser.lastLoginAt, activatedUser.lastLoginAt)
            assertNull(activatedUser.deactivatedAt)
        }

        @Test
        @DisplayName("탈퇴 사용자는 활성화할 수 없다")
        fun rejectActivationOfDeletedUser() {
            val deletedUser = deletedUser()

            assertFailsWith<IllegalArgumentException> {
                deletedUser.activate()
            }
        }
    }

    @Nested
    @DisplayName("recordLogin()")
    inner class RecordLogin {

        @Test
        @DisplayName("활성 사용자는 로그인 시각을 기록할 수 있다")
        fun activeUserCanRecordLoginTime() {
            val user = activeUser()
            val loggedInAt = Instant.parse("2026-05-20T03:00:00Z")

            val loggedInUser = user.recordLogin(loggedInAt)

            assertEquals(loggedInAt, loggedInUser.lastLoginAt)
            assertEquals(UserStatus.ACTIVE, loggedInUser.status)
        }

        @Test
        @DisplayName("활성 상태가 아닌 사용자는 로그인 시각을 기록할 수 없다")
        fun rejectLoginTimeRecordOfNonActiveUser() {
            val inactiveUser = User.restore(
                id = UserId.of(UUID.randomUUID()),
                email = email,
                nickname = nickname,
                status = UserStatus.INACTIVE,
                role = UserRole.USER,
                authProvider = AuthProvider.KAKAO,
                registeredAt = registeredAt,
                lastLoginAt = null,
                deactivatedAt = null
            )

            assertFailsWith<IllegalArgumentException> {
                inactiveUser.recordLogin(Instant.parse("2026-05-20T03:00:00Z"))
            }
        }
    }

    @Nested
    @DisplayName("deactivate()")
    inner class Deactivate {

        @Test
        @DisplayName("활성 사용자는 탈퇴할 수 있다")
        fun activeUserCanBeDeactivated() {
            val user = activeUser().recordLogin(Instant.parse("2026-05-20T03:00:00Z"))
            val deactivatedAt = Instant.parse("2026-05-20T04:00:00Z")

            val deactivatedUser = user.deactivate(deactivatedAt)

            assertEquals(UserStatus.DELETED, deactivatedUser.status)
            assertEquals(user.lastLoginAt, deactivatedUser.lastLoginAt)
            assertEquals(deactivatedAt, deactivatedUser.deactivatedAt)
        }

        @Test
        @DisplayName("탈퇴 사용자는 다시 탈퇴할 수 없다")
        fun rejectDeactivationOfDeletedUser() {
            val deletedUser = deletedUser()

            assertFailsWith<IllegalArgumentException> {
                deletedUser.deactivate(Instant.parse("2026-05-20T04:00:00Z"))
            }
        }
    }

    private fun activeUser(): User {
        return User.register(
            email = email,
            nickname = nickname,
            authProvider = AuthProvider.GOOGLE,
            registeredAt = registeredAt
        )
    }

    private fun deletedUser(): User {
        return User.restore(
            id = UserId.of(UUID.randomUUID()),
            email = email,
            nickname = nickname,
            status = UserStatus.DELETED,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            registeredAt = registeredAt,
            lastLoginAt = null,
            deactivatedAt = Instant.parse("2026-05-20T02:00:00Z")
        )
    }
}
