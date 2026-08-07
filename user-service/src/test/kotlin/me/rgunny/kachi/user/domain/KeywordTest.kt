package me.rgunny.kachi.user.domain

import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@DisplayName("Keyword")
class KeywordTest {
    private val userId = UserId.of(UUID.randomUUID())
    private val name = KeywordName.of("NVIDIA")
    private val registeredAt = UserTestFixture.NOW

    @Nested
    @DisplayName("create()")
    inner class Create {
        @Test
        @DisplayName("키워드를 생성하면 활성 상태가 된다")
        fun createKeywordAsEnabled() {
            val keyword = Keyword.create(
                userId = userId,
                name = name,
                registeredAt = registeredAt
            )

            assertNotNull(keyword.id.value)
            assertEquals(userId, keyword.userId)
            assertEquals(name, keyword.name)
            assertEquals(true, keyword.enabled)
            assertEquals(registeredAt, keyword.registeredAt)
            assertNull(keyword.disabledAt)
        }
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {
        @Test
        @DisplayName("키워드를 복원하면 저장된 상태를 그대로 가진다")
        fun restoreKeywordFromPersistedState() {
            val id = KeywordId.of(UUID.randomUUID())
            val disabledAt = registeredAt.plus(Duration.ofHours(1))

            val keyword = Keyword.restore(
                id = id,
                userId = userId,
                name = name,
                enabled = false,
                registeredAt = registeredAt,
                disabledAt = disabledAt
            )

            assertEquals(id, keyword.id)
            assertEquals(userId, keyword.userId)
            assertEquals(name, keyword.name)
            assertEquals(false, keyword.enabled)
            assertEquals(registeredAt, keyword.registeredAt)
            assertEquals(disabledAt, keyword.disabledAt)
        }
    }

    @Nested
    @DisplayName("rename()")
    inner class Rename {
        @Test
        @DisplayName("키워드 이름을 변경한다")
        fun renameKeyword() {
            val keyword = activeKeyword()
            val newName = KeywordName.of("Tesla")

            val renamedKeyword = keyword.rename(newName)

            assertEquals(newName, renamedKeyword.name)
            assertEquals(keyword.id, renamedKeyword.id)
            assertEquals(keyword.enabled, renamedKeyword.enabled)
        }
    }

    @Nested
    @DisplayName("enable()")
    inner class Enable {
        @Test
        @DisplayName("비활성 키워드를 활성화하고 비활성 시간을 제거한다")
        fun enableKeyword() {
            val disabledKeyword = activeKeyword().disable(registeredAt.plus(Duration.ofHours(1)))

            val enabledKeyword = disabledKeyword.enable()

            assertEquals(true, enabledKeyword.enabled)
            assertNull(enabledKeyword.disabledAt)
        }
    }

    @Nested
    @DisplayName("disable()")
    inner class Disable {
        @Test
        @DisplayName("활성 키워드를 비활성화한다")
        fun disableKeyword() {
            val keyword = activeKeyword()
            val disabledAt = registeredAt.plus(Duration.ofHours(1))

            val disabledKeyword = keyword.disable(disabledAt)

            assertEquals(false, disabledKeyword.enabled)
            assertEquals(disabledAt, disabledKeyword.disabledAt)
        }

        @Test
        @DisplayName("이미 비활성화된 키워드는 다시 비활성화할 수 없다")
        fun rejectDisablingAlreadyDisabledKeyword() {
            val disabledKeyword = activeKeyword().disable(registeredAt.plus(Duration.ofHours(1)))

            assertFailsWith<IllegalArgumentException> {
                disabledKeyword.disable(registeredAt.plus(Duration.ofHours(2)))
            }
        }
    }

    private fun activeKeyword(): Keyword {
        return Keyword.create(
            userId = userId,
            name = name,
            registeredAt = registeredAt
        )
    }
}
