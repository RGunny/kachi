package me.rgunny.kachi.user.adapter.out.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("UserPersistenceAdapter 통합 테스트")
class UserPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {
    @Autowired
    private lateinit var userPersistenceAdapter: UserPersistenceAdapter

    @Autowired
    private lateinit var entityManager: EntityManager

    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("User 도메인을 MySQL에 저장하고 다시 조회한다")
        fun saveUserAndFindById() {
            val user = localUser(email = "rgunny@kachi.com")

            val savedUser = userPersistenceAdapter.save(user)
            flushAndClear()

            val foundUser = userPersistenceAdapter.findById(savedUser.id)

            assertNotNull(foundUser)
            assertEquals(savedUser.id, foundUser.id)
            assertEquals(Email.of("rgunny@kachi.com"), foundUser.email)
            assertEquals(Nickname.of("rgunny"), foundUser.nickname)
            assertEquals(AuthProvider.LOCAL, foundUser.authProvider)
        }

        @Test
        @DisplayName("email은 MySQL unique 제약으로 중복 저장할 수 없다")
        fun rejectDuplicateEmail() {
            userPersistenceAdapter.save(localUser(email = "rgunny@kachi.com", nickname = "rgunny"))
            userPersistenceAdapter.save(localUser(email = "rgunny@kachi.com", nickname = "gunny"))

            assertFailsWith<PersistenceException> {
                flushAndClear()
            }
        }
    }

    @Nested
    @DisplayName("findByAuthProviderAndProviderUserId()")
    inner class FindByAuthProviderAndProviderUserId {

        @Test
        @DisplayName("OAuth provider와 provider 사용자 ID로 User를 조회한다")
        fun findOAuthUser() {
            val user = oauthUser(
                email = "oauth@kachi.com",
                providerUserId = "google-12345"
            )

            val savedUser = userPersistenceAdapter.save(user)
            flushAndClear()

            val foundUser = userPersistenceAdapter.findByAuthProviderAndProviderUserId(
                authProvider = AuthProvider.GOOGLE,
                providerUserId = ProviderUserId.of("google-12345")
            )

            assertNotNull(foundUser)
            assertEquals(savedUser.id, foundUser.id)
            assertEquals(ProviderUserId.of("google-12345"), foundUser.providerUserId)
        }
    }

    @Nested
    @DisplayName("existsByEmail()")
    inner class ExistsByEmail {

        @Test
        @DisplayName("저장된 email 존재 여부를 MySQL에서 확인한다")
        fun existsByEmail() {
            userPersistenceAdapter.save(localUser(email = "rgunny@kachi.com"))
            flushAndClear()

            val exists = userPersistenceAdapter.existsByEmail(Email.of("rgunny@kachi.com"))

            assertTrue(exists)
        }
    }

    private fun localUser(
        email: String,
        nickname: String = "rgunny"
    ): User {
        return User.register(
            email = Email.of(email),
            nickname = Nickname.of(nickname),
            authProvider = AuthProvider.LOCAL,
            providerUserId = null,
            registeredAt = registeredAt
        )
    }

    private fun oauthUser(
        email: String,
        providerUserId: String
    ): User {
        return User.register(
            email = Email.of(email),
            nickname = Nickname.of("oauth-user"),
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of(providerUserId),
            registeredAt = registeredAt
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
