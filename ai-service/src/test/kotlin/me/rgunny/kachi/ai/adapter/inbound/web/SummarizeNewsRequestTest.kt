package me.rgunny.kachi.ai.adapter.inbound.web

import me.rgunny.kachi.ai.application.port.inbound.news.model.SummaryWindowRequest
import me.rgunny.kachi.ai.domain.keyword.AiKeyword
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@DisplayName("SummarizeNewsRequest")
class SummarizeNewsRequestTest {

    private val from = AiTestFixture.NOW.minus(Duration.ofDays(2))
    private val to = from.plus(Duration.ofDays(1))

    @Test
    @DisplayName("뉴스 요약 요청을 command로 변환한다")
    fun convertToCommand() {
        val command = SummarizeNewsRequest(
            keywords = listOf("NVIDIA"),
            from = from,
            to = to,
            maxArticlesPerKeyword = 10
        ).toCommand()

        val window = assertIs<SummaryWindowRequest.Explicit>(command.window)
        assertEquals(listOf(AiKeyword.of("NVIDIA")), command.keywords)
        assertEquals(from, window.from)
        assertEquals(to, window.to)
        assertEquals(10, command.maxArticlesPerKeyword)
    }

    @Test
    @DisplayName("시작 시각이 종료 시각보다 이후이면 실패한다")
    fun rejectInvertedWindow() {
        assertFailsWith<IllegalArgumentException> {
            SummarizeNewsRequest(
                from = to,
                to = from
            ).toCommand()
        }
    }

    @Test
    @DisplayName("최대 뉴스 개수가 범위를 벗어나면 실패한다")
    fun rejectInvalidMaxArticlesPerKeyword() {
        assertFailsWith<IllegalArgumentException> {
            SummarizeNewsRequest(maxArticlesPerKeyword = 0).toCommand()
        }
    }
}
