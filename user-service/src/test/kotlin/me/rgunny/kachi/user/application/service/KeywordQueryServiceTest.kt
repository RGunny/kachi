package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.port.`in`.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.application.port.out.UserPersistencePort
import me.rgunny.kachi.user.domain.AuthProvider
import me.rgunny.kachi.user.domain.Email
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.Nickname
import me.rgunny.kachi.user.domain.ProviderUserId
import me.rgunny.kachi.user.domain.User
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.domain.UserRole
import me.rgunny.kachi.user.domain.UserStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("KeywordQueryService")
class KeywordQueryServiceTest {
    private val userId = UserId.newId()
    private val registeredAt = Instant.parse("2026-05-20T00:00:00Z")

    @Nested
    @DisplayName("list()")
    inner class ListKeywords {

        @Test
        @DisplayName("사용자 관심 키워드 목록을 등록일 역순으로 조회한다")
        fun listKeywords() {
            val olderKeyword = keyword(name = "Trump", registeredAt = registeredAt)
            val newerKeyword = keyword(name = "Tesla", registeredAt = Instant.parse("2026-05-20T01:00:00Z"))
            val keywordPersistencePort = FakeKeywordPersistencePort(
                keywords = listOf(olderKeyword, newerKeyword)
            )
            val service = keywordQueryService(keywordPersistencePort)

            val results = service.list(ListKeywordsQuery(userId))

            assertEquals(userId, keywordPersistencePort.userId)
            assertEquals(listOf("Tesla", "Trump"), results.map { it.name })
            assertEquals(listOf(newerKeyword.id, olderKeyword.id), results.map { it.id })
        }

        @Test
        @DisplayName("활성 사용자가 아니면 조회할 수 없다")
        fun rejectInactiveUser() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = keywordQueryService(
                keywordPersistencePort = keywordPersistencePort,
                users = mapOf(userId to user(status = UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.list(ListKeywordsQuery(userId))
            }
        }
    }

    private fun keywordQueryService(
        keywordPersistencePort: KeywordPersistencePort,
        users: Map<UserId, User> = mapOf(userId to user())
    ): KeywordQueryService {
        val userPersistencePort = FakeUserPersistencePort(users)

        return KeywordQueryService(
            keywordPersistencePort = keywordPersistencePort,
            activeUserValidator = ActiveUserValidator(userPersistencePort)
        )
    }

    private class FakeKeywordPersistencePort(
        private val keywords: List<Keyword> = emptyList()
    ) : KeywordPersistencePort {
        var userId: UserId? = null

        override fun findById(keywordId: KeywordId): Keyword? {
            return null
        }

        override fun findAllByUserId(userId: UserId): List<Keyword> {
            this.userId = userId
            return keywords
        }

        override fun findAllEnabled(): List<Keyword> {
            return keywords.filter { it.enabled }
        }

        override fun existsByUserIdAndName(userId: UserId, name: KeywordName): Boolean {
            return false
        }

        override fun save(keyword: Keyword): Keyword {
            return keyword
        }
    }

    private fun keyword(name: String, registeredAt: Instant): Keyword {
        return Keyword.create(
            userId = userId,
            name = KeywordName.of(name),
            registeredAt = registeredAt
        )
    }

    private fun user(status: UserStatus = UserStatus.ACTIVE): User {
        return User.restore(
            id = userId,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = status,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = registeredAt,
            lastLoginAt = null,
            deactivatedAt = null
        )
    }

    private class FakeUserPersistencePort(
        private val users: Map<UserId, User>
    ) : UserPersistencePort {

        override fun findById(userId: UserId): User? {
            return users[userId]
        }

        override fun findByAuthProviderAndProviderUserId(
            authProvider: AuthProvider,
            providerUserId: ProviderUserId
        ): User? {
            return null
        }

        override fun existsByEmail(email: Email): Boolean {
            return false
        }

        override fun save(user: User): User {
            return user
        }
    }
}
