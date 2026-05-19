package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.exception.DuplicateKeywordException
import me.rgunny.kachi.user.application.port.`in`.RegisterKeywordCommand
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
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

    private class FakeKeywordPersistencePort(
        private val existingPairs: Set<Pair<UserId, KeywordName>> = emptySet()
    ) : KeywordPersistencePort {
        val savedKeywords = mutableListOf<Keyword>()
        var existsByUserIdAndNameCalled = false
        var saveCalled = false

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
}
