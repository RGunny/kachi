package me.rgunny.kachi.user.adapter.outbound.persistence

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KeywordPersistenceAdapter 통합 테스트")
class KeywordPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var keywordPersistenceAdapter: KeywordPersistenceAdapter

    @Autowired
    private lateinit var entityManager: EntityManager

    private val registeredAt = UserTestFixture.NOW

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("Keyword 도메인을 MySQL에 저장하고 다시 조회한다")
        fun saveKeywordAndFindById() {
            val userId = UserId.newId()
            val keyword = keyword(userId = userId, name = "Trump")

            val savedKeyword = keywordPersistenceAdapter.save(keyword)
            flushAndClear()

            val foundKeyword = keywordPersistenceAdapter.findById(savedKeyword.id)

            assertNotNull(foundKeyword)
            assertEquals(savedKeyword.id, foundKeyword.id)
            assertEquals(userId, foundKeyword.userId)
            assertEquals(KeywordName.of("Trump"), foundKeyword.name)
            assertTrue(foundKeyword.enabled)
        }

        @Test
        @DisplayName("같은 사용자는 같은 이름의 Keyword를 중복 저장할 수 없다")
        fun rejectDuplicateUserKeywordName() {
            val userId = UserId.newId()

            keywordPersistenceAdapter.save(keyword(userId = userId, name = "Trump"))
            keywordPersistenceAdapter.save(keyword(userId = userId, name = "Trump"))

            assertFailsWith<PersistenceException> {
                flushAndClear()
            }
        }
    }

    @Nested
    @DisplayName("findAllByUserId()")
    inner class FindAllByUserId {

        @Test
        @DisplayName("사용자 ID로 Keyword 목록을 조회한다")
        fun findAllByUserId() {
            val userId = UserId.newId()
            keywordPersistenceAdapter.save(keyword(userId = userId, name = "Trump"))
            keywordPersistenceAdapter.save(keyword(userId = userId, name = "Nvidia"))
            keywordPersistenceAdapter.save(keyword(userId = UserId.newId(), name = "Bitcoin"))
            flushAndClear()

            val keywords = keywordPersistenceAdapter.findAllByUserId(userId)

            assertEquals(2, keywords.size)
            assertEquals(setOf("Trump", "Nvidia"), keywords.map { it.name.value }.toSet())
        }
    }

    @Nested
    @DisplayName("existsByUserIdAndName()")
    inner class ExistsByUserIdAndName {

        @Test
        @DisplayName("사용자 ID와 이름으로 Keyword 존재 여부를 MySQL에서 확인한다")
        fun existsByUserIdAndName() {
            val userId = UserId.newId()
            keywordPersistenceAdapter.save(keyword(userId = userId, name = "Trump"))
            flushAndClear()

            val exists = keywordPersistenceAdapter.existsByUserIdAndName(
                userId = userId,
                name = KeywordName.of("Trump")
            )

            assertTrue(exists)
        }
    }

    private fun keyword(userId: UserId, name: String): Keyword {
        return Keyword.create(
            userId = userId,
            name = KeywordName.of(name),
            registeredAt = registeredAt
        )
    }

    private fun flushAndClear() {
        entityManager.flush()
        entityManager.clear()
    }
}
