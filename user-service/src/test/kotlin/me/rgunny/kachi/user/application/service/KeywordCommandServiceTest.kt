package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.InactiveUserException
import me.rgunny.kachi.user.application.exception.KeywordAccessDeniedException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.application.exception.UserNotFoundException
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
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
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KeywordCommandService")
class KeywordCommandServiceTest {
    private val now = UserTestFixture.NOW
    private val clock = UserTestFixture.CLOCK
    private val userId = UserId.of(UUID.randomUUID())

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("관심 키워드를 등록하고 저장한다")
        fun registerKeyword() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = keywordCommandService(keywordPersistencePort)

            val result = service.register(
                RegisterKeywordCommand(
                    userId = userId,
                    name = "  TRUMP  "
                )
            )

            assertNotNull(result.id.value)
            assertEquals(userId, result.userId)
            assertEquals("TRUMP", result.name)
            assertEquals(true, result.enabled)
            assertEquals(now, result.registeredAt)
            assertEquals(1, keywordPersistencePort.savedKeywords.size)
            assertEquals(result.id, keywordPersistencePort.savedKeywords.single().id)
        }

        @Test
        @DisplayName("이미 등록된 키워드이면 저장하지 않는다")
        fun rejectDuplicateKeyword() {
            val keywordPersistencePort = FakeKeywordPersistencePort(
                existingPairs = setOf(userId to KeywordName.of("TRUMP"))
            )
            val service = keywordCommandService(keywordPersistencePort)

            assertFailsWith<DuplicateKeywordException> {
                service.register(
                    RegisterKeywordCommand(
                        userId = userId,
                        name = "TRUMP"
                    )
                )
            }

            assertTrue(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("사용자가 없으면 키워드를 등록할 수 없다")
        fun rejectMissingUser() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = keywordCommandService(
                keywordPersistencePort = keywordPersistencePort,
                users = emptyMap()
            )

            assertFailsWith<UserNotFoundException> {
                service.register(
                    RegisterKeywordCommand(
                        userId = userId,
                        name = "Trump"
                    )
                )
            }

            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("활성 사용자가 아니면 키워드를 등록할 수 없다")
        fun rejectInactiveUser() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = keywordCommandService(
                keywordPersistencePort = keywordPersistencePort,
                users = mapOf(userId to user(status = UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.register(
                    RegisterKeywordCommand(
                        userId = userId,
                        name = "Trump"
                    )
                )
            }

            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }
    }

    @Nested
    @DisplayName("update()")
    inner class Update {

        @Test
        @DisplayName("관심 키워드 이름을 변경하고 저장한다")
        fun updateKeywordName() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = keywordCommandService(keywordPersistencePort)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
                    userId = userId,
                    name = "  Tesla  "
                )
            )

            assertEquals(keyword.id, result.id)
            assertEquals(userId, result.userId)
            assertEquals("Tesla", result.name)
            assertEquals(true, result.enabled)
            assertEquals(keyword.registeredAt, result.registeredAt)
            assertEquals(null, result.disabledAt)
            assertEquals("Tesla", keywordPersistencePort.savedKeywords.single().name.value)
        }

        @Test
        @DisplayName("같은 이름으로 변경하면 중복 확인 없이 저장한다")
        fun renameKeywordToSameName() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(
                existingPairs = setOf(userId to KeywordName.of("Trump")),
                keywords = mapOf(keyword.id to keyword)
            )
            val service = keywordCommandService(keywordPersistencePort)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
                    userId = userId,
                    name = "Trump"
                )
            )

            assertEquals("Trump", result.name)
            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertTrue(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("활성 키워드를 비활성화한다")
        fun disableKeyword() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = keywordCommandService(keywordPersistencePort)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
                    userId = userId,
                    enabled = false
                )
            )

            assertEquals(false, result.enabled)
            assertEquals(now, result.disabledAt)
            assertEquals(false, keywordPersistencePort.savedKeywords.single().enabled)
        }

        @Test
        @DisplayName("비활성 키워드를 활성화한다")
        fun enableKeyword() {
            val keyword = activeKeyword(name = "Trump").disable(now)
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = keywordCommandService(keywordPersistencePort)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
                    userId = userId,
                    enabled = true
                )
            )

            assertEquals(true, result.enabled)
            assertEquals(null, result.disabledAt)
            assertEquals(true, keywordPersistencePort.savedKeywords.single().enabled)
        }

        @Test
        @DisplayName("수정할 값이 없으면 실패한다")
        fun rejectEmptyUpdateCommand() {
            assertFailsWith<IllegalArgumentException> {
                UpdateKeywordCommand(
                    keywordId = KeywordId.of(UUID.randomUUID()),
                    userId = userId
                )
            }
        }

        @Test
        @DisplayName("변경할 키워드가 없으면 실패한다")
        fun rejectMissingKeyword() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = keywordCommandService(keywordPersistencePort)

            assertFailsWith<KeywordNotFoundException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = KeywordId.of(UUID.randomUUID()),
                        userId = userId,
                        name = "Tesla"
                    )
                )
            }

            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("이미 등록된 이름으로 변경할 수 없다")
        fun rejectDuplicateKeywordName() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(
                existingPairs = setOf(userId to KeywordName.of("Tesla")),
                keywords = mapOf(keyword.id to keyword)
            )
            val service = keywordCommandService(keywordPersistencePort)

            assertFailsWith<DuplicateKeywordException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = keyword.id,
                        userId = userId,
                        name = "Tesla"
                    )
                )
            }

            assertTrue(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("키워드 소유 사용자가 활성 사용자가 아니면 수정할 수 없다")
        fun rejectInactiveUserOnUpdate() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = keywordCommandService(
                keywordPersistencePort = keywordPersistencePort,
                users = mapOf(userId to user(status = UserStatus.DELETED))
            )

            assertFailsWith<InactiveUserException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = keyword.id,
                        userId = userId,
                        name = "Tesla"
                    )
                )
            }

            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }

        @Test
        @DisplayName("키워드 소유자가 아니면 수정할 수 없다")
        fun rejectNonOwnerUser() {
            val keyword = activeKeyword(name = "Trump")
            val otherUserId = UserId.of(UUID.randomUUID())
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = keywordCommandService(
                keywordPersistencePort = keywordPersistencePort,
                users = mapOf(userId to user(), otherUserId to user(id = otherUserId))
            )

            assertFailsWith<KeywordAccessDeniedException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = keyword.id,
                        userId = otherUserId,
                        name = "Tesla"
                    )
                )
            }

            assertFalse(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }
    }

    private fun keywordCommandService(
        keywordPersistencePort: KeywordPersistencePort,
        users: Map<UserId, User> = mapOf(userId to user())
    ): KeywordCommandService {
        val userPersistencePort = FakeUserPersistencePort(users)

        return KeywordCommandService(
            keywordPersistencePort = keywordPersistencePort,
            activeUserValidator = ActiveUserValidator(userPersistencePort),
            clock = clock
        )
    }

    private class FakeKeywordPersistencePort(
        private val existingPairs: Set<Pair<UserId, KeywordName>> = emptySet(),
        private val keywords: Map<KeywordId, Keyword> = emptyMap()
    ) : KeywordPersistencePort {
        val savedKeywords = mutableListOf<Keyword>()
        var existsByUserIdAndNameCalled = false
        var saveCalled = false

        override fun findById(keywordId: KeywordId): Keyword? {
            return keywords[keywordId]
        }

        override fun findAllByUserId(userId: UserId): List<Keyword> {
            return keywords.values.filter { it.userId == userId }
        }

        override fun findAllEnabled(): List<Keyword> {
            return keywords.values.filter { it.enabled }
        }

        override fun existsByUserIdAndName(userId: UserId, name: KeywordName): Boolean {
            existsByUserIdAndNameCalled = true
            return userId to name in existingPairs
        }

        override fun save(keyword: Keyword): Keyword {
            saveCalled = true
            savedKeywords += keyword
            return keyword
        }
    }

    private fun activeKeyword(name: String): Keyword {
        return Keyword.create(
            userId = userId,
            name = KeywordName.of(name),
            registeredAt = now
        )
    }

    private fun user(
        id: UserId = userId,
        status: UserStatus = UserStatus.ACTIVE
    ): User {
        return User.restore(
            id = id,
            email = Email.of("rgunny@kachi.com"),
            nickname = Nickname.of("rgunny"),
            status = status,
            role = UserRole.USER,
            authProvider = AuthProvider.GOOGLE,
            providerUserId = ProviderUserId.of("google-123"),
            registeredAt = now,
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
