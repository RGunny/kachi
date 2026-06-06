package me.rgunny.kachi.collector.adapter.`in`.web

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.application.port.`in`.ListNewsQuery
import me.rgunny.kachi.collector.application.port.`in`.ListNewsResult
import me.rgunny.kachi.collector.application.port.`in`.ListNewsUseCase
import me.rgunny.kachi.collector.domain.CollectedKeyword
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("InternalNewsController")
class InternalNewsControllerTest {

    @Test
    @DisplayName("저장 뉴스 조회 요청을 query로 변환해 실행한다")
    fun listNews() = runBlocking {
        val useCase = CountingListNewsUseCase(
            results = listOf(
                ListNewsResult(
                    id = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                    source = "GOOGLE",
                    title = "NVIDIA 실적 발표",
                    url = "https://kachi.com/news/1",
                    publishedAt = Instant.parse("2026-06-01T10:00:00Z"),
                    collectedAt = Instant.parse("2026-06-01T10:05:00Z"),
                    matchedKeywords = listOf("NVIDIA")
                )
            )
        )
        val controller = InternalNewsController(useCase)

        val response = controller.listNews(
            keyword = " NVIDIA ",
            from = Instant.parse("2026-06-01T00:00:00Z"),
            to = Instant.parse("2026-06-02T00:00:00Z"),
            limit = 10
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(CollectedKeyword.of("NVIDIA"), useCase.query.keyword)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), useCase.query.from)
        assertEquals(Instant.parse("2026-06-02T00:00:00Z"), useCase.query.to)
        assertEquals(10, useCase.query.limit)

        val body = response.body as ApiResponse<*>
        val data = body.data as List<*>
        assertEquals(true, body.success)
        assertEquals("NVIDIA 실적 발표", (data.first() as ListNewsResult).title)
    }

    @Test
    @DisplayName("limit이 허용 범위를 벗어나면 400 응답을 반환한다")
    fun returnBadRequestWhenLimitIsInvalid() = runBlocking {
        val controller = InternalNewsController(CountingListNewsUseCase())

        val response = controller.listNews(
            keyword = "NVIDIA",
            from = null,
            to = null,
            limit = 101
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as ApiResponse<*>
        assertEquals(false, body.success)
        assertEquals("INVALID_NEWS_QUERY", body.error?.code)
    }

    @Test
    @DisplayName("from이 to보다 이후이면 400 응답을 반환한다")
    fun returnBadRequestWhenFromIsAfterTo() = runBlocking {
        val controller = InternalNewsController(CountingListNewsUseCase())

        val response = controller.listNews(
            keyword = "NVIDIA",
            from = Instant.parse("2026-06-02T00:00:00Z"),
            to = Instant.parse("2026-06-01T00:00:00Z"),
            limit = 20
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as ApiResponse<*>
        assertEquals(false, body.success)
        assertEquals("INVALID_NEWS_QUERY", body.error?.code)
    }

    private class CountingListNewsUseCase(
        private val results: List<ListNewsResult> = emptyList()
    ) : ListNewsUseCase {
        lateinit var query: ListNewsQuery

        override suspend fun listNews(query: ListNewsQuery): List<ListNewsResult> {
            this.query = query
            return results
        }
    }
}
