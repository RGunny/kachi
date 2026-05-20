package me.rgunny.kachi.user.application.service

import me.rgunny.kachi.user.application.port.`in`.ListKeywordsQuery
import me.rgunny.kachi.user.application.port.out.KeywordPersistencePort
import me.rgunny.kachi.user.domain.Keyword
import me.rgunny.kachi.user.domain.KeywordId
import me.rgunny.kachi.user.domain.KeywordName
import me.rgunny.kachi.user.domain.UserId
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("KeywordQueryService")
class KeywordQueryServiceTest {
    private val userId = UserId.newId()

    @Test
    @DisplayName("사용자 관심 키워드 목록을 등록일 역순으로 조회한다")
    fun listKeywords() {
        val olderKeyword = keyword(name = "Trump", registeredAt = Instant.parse("2026-05-20T00:00:00Z"))
        val newerKeyword = keyword(name = "Tesla", registeredAt = Instant.parse("2026-05-20T01:00:00Z"))
        val keywordPersistencePort = FakeKeywordPersistencePort(
            keywords = listOf(olderKeyword, newerKeyword)
        )
        val service = KeywordQueryService(keywordPersistencePort)

        val results = service.list(ListKeywordsQuery(userId))

        assertEquals(userId, keywordPersistencePort.userId)
        assertEquals(listOf("Tesla", "Trump"), results.map { it.name })
        assertEquals(listOf(newerKeyword.id, olderKeyword.id), results.map { it.id })
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
}
