package me.rgunny.kachi.ai.domain.summary

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@DisplayName("NewsHash")
class NewsHashTest {

    private val firstNewsId = UUID.fromString("018f0000-0000-7000-8000-000000000001")
    private val secondNewsId = UUID.fromString("018f0000-0000-7000-8000-000000000002")

    @Test
    @DisplayName("뉴스 id 순서가 달라도 같은 hash를 반환한다")
    fun returnSameHashWhenNewsIdsHaveDifferentOrder() {
        val first = NewsHash.calculate(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(firstNewsId, secondNewsId)
        )
        val second = NewsHash.calculate(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(secondNewsId, firstNewsId)
        )

        assertEquals(first, second)
    }

    @Test
    @DisplayName("키워드가 다르면 다른 hash를 반환한다")
    fun returnDifferentHashWhenKeywordIsDifferent() {
        val base = NewsHash.calculate(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(firstNewsId)
        )
        val differentKeyword = NewsHash.calculate(
            keyword = AiKeyword.of("TESLA"),
            sourceNewsIds = listOf(firstNewsId)
        )

        assertNotEquals(base, differentKeyword)
    }

    @Test
    @DisplayName("뉴스 id가 추가되면 다른 hash를 반환한다")
    fun returnDifferentHashWhenNewsIdIsAdded() {
        val base = NewsHash.calculate(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(firstNewsId)
        )
        val added = NewsHash.calculate(
            keyword = AiKeyword.of("NVIDIA"),
            sourceNewsIds = listOf(firstNewsId, secondNewsId)
        )

        assertNotEquals(base, added)
    }

    @Test
    @DisplayName("뉴스 id가 없으면 hash를 계산할 수 없다")
    fun rejectEmptyNewsIds() {
        assertFailsWith<IllegalArgumentException> {
            NewsHash.calculate(
                keyword = AiKeyword.of("NVIDIA"),
                sourceNewsIds = emptyList()
            )
        }
    }
}
