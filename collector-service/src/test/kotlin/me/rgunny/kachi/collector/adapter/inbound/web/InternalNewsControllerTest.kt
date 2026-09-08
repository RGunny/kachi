package me.rgunny.kachi.collector.adapter.inbound.web

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.inbound.web.response.ApiResponse
import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsQuery
import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsResult
import me.rgunny.kachi.collector.application.port.inbound.news.ListNewsUseCase
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

@DisplayName("InternalNewsController")
class InternalNewsControllerTest {

    private val from = CollectorTestFixture.NOW
    private val to = from.plus(Duration.ofDays(1))

    @Test
    @DisplayName("저장 뉴스 조회 요청을 query로 변환해 실행한다")
    fun listNews() = runBlocking {
        val useCase = CountingListNewsUseCase(
            results = listOf(
                ListNewsResult(
                    id = UUID.fromString("018f0000-0000-7000-8000-000000000001"),
                    source = "GOOGLE",
                    title = "NVIDIA 실적 발표",
                    excerpt = "엔비디아 실적",
                    url = "https://kachi.com/news/1",
                    language = "ko",
                    publishedAt = from.plus(Duration.ofHours(10)),
                    collectedAt = from.plus(Duration.ofHours(10)).plusSeconds(300),
                    matchedKeywords = listOf("NVIDIA")
                )
            )
        )
        val controller = InternalNewsController(useCase)

        val response = controller.listNews(
            keyword = " NVIDIA ",
            from = from,
            to = to,
            limit = 10
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(CollectedKeyword.of("NVIDIA"), useCase.query.keyword)
        assertEquals(from, useCase.query.from)
        assertEquals(to, useCase.query.to)
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
            from = to,
            to = from,
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
