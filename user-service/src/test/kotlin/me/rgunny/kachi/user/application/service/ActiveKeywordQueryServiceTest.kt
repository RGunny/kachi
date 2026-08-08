package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.outbound.keyword.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import me.rgunny.kachi.user.fixture.UserTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals

@DisplayName("ActiveKeywordQueryService")
class ActiveKeywordQueryServiceTest {
    private val userId = UserId.newId()
    private val registeredAt = UserTestFixture.NOW.plus(Duration.ofDays(8))

    @Nested
    @DisplayName("listActiveKeywords()")
    inner class ListActiveKeywords {

        @Test
        @DisplayName("활성 키워드를 이름 기준으로 중복 제거해서 조회한다")
        fun listActiveKeywords() {
            val service = ActiveKeywordQueryService(
                keywordPersistencePort = FakeKeywordPersistencePort(
                    keywords = listOf(
                        keyword("NVIDIA"),
                        keyword("Tesla"),
                        keyword("NVIDIA")
                    )
                )
            )

            val results = service.listActiveKeywords()

            assertEquals(listOf("NVIDIA", "Tesla"), results.map { it.name })
        }
    }

    private fun keyword(name: String): Keyword {
        return Keyword.create(
            userId = userId,
            name = KeywordName.of(name),
            registeredAt = registeredAt
        )
    }

    private class FakeKeywordPersistencePort(
        private val keywords: List<Keyword>
    ) : KeywordPersistencePort {

        override fun findById(keywordId: KeywordId): Keyword? {
            return null
        }

        override fun findAllByUserId(userId: UserId): List<Keyword> {
            return keywords.filter { it.userId == userId }
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
}
