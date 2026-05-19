package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.exception.KeywordNotFoundException
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.`in`.UpdateKeywordCommand
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KeywordCommandService")
class KeywordCommandServiceTest {
    private val now = Instant.parse("2026-05-20T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val userId = UserId.of(UUID.randomUUID())

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("관심 키워드를 등록하고 저장한다")
        fun registerKeyword() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = KeywordCommandService(keywordPersistencePort, clock)

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
            val service = KeywordCommandService(keywordPersistencePort, clock)

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
    }

    @Nested
    @DisplayName("update()")
    inner class Update {

        @Test
        @DisplayName("관심 키워드 이름을 변경하고 저장한다")
        fun updateKeywordName() {
            val keyword = activeKeyword(name = "Trump")
            val keywordPersistencePort = FakeKeywordPersistencePort(keywords = mapOf(keyword.id to keyword))
            val service = KeywordCommandService(keywordPersistencePort, clock)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
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
            val service = KeywordCommandService(keywordPersistencePort, clock)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
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
            val service = KeywordCommandService(keywordPersistencePort, clock)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
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
            val service = KeywordCommandService(keywordPersistencePort, clock)

            val result = service.update(
                UpdateKeywordCommand(
                    keywordId = keyword.id,
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
                    keywordId = KeywordId.of(UUID.randomUUID())
                )
            }
        }

        @Test
        @DisplayName("변경할 키워드가 없으면 실패한다")
        fun rejectMissingKeyword() {
            val keywordPersistencePort = FakeKeywordPersistencePort()
            val service = KeywordCommandService(keywordPersistencePort, clock)

            assertFailsWith<KeywordNotFoundException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = KeywordId.of(UUID.randomUUID()),
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
            val service = KeywordCommandService(keywordPersistencePort, clock)

            assertFailsWith<DuplicateKeywordException> {
                service.update(
                    UpdateKeywordCommand(
                        keywordId = keyword.id,
                        name = "Tesla"
                    )
                )
            }

            assertTrue(keywordPersistencePort.existsByUserIdAndNameCalled)
            assertFalse(keywordPersistencePort.saveCalled)
        }
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
}
