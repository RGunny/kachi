package me.rgunny.kachi.ai.adapter.`in`.web

import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("SummarizeNewsRequest")
class SummarizeNewsRequestTest {

    @Test
    @DisplayName("뉴스 요약 요청을 command로 변환한다")
    fun convertToCommand() {
        val from = Instant.parse("2026-06-01T00:00:00Z")
        val to = Instant.parse("2026-06-02T00:00:00Z")

        val command = SummarizeNewsRequest(
            keywords = listOf("NVIDIA"),
            from = from,
            to = to,
            maxArticlesPerKeyword = 10
        ).toCommand()

        assertEquals(listOf(AiKeyword.of("NVIDIA")), command.keywords)
        assertEquals(from, command.from)
        assertEquals(to, command.to)
        assertEquals(10, command.maxArticlesPerKeyword)
    }

    @Test
    @DisplayName("최대 뉴스 개수가 범위를 벗어나면 실패한다")
    fun rejectInvalidMaxArticlesPerKeyword() {
        assertFailsWith<IllegalArgumentException> {
            SummarizeNewsRequest(maxArticlesPerKeyword = 0).toCommand()
        }
    }
}
