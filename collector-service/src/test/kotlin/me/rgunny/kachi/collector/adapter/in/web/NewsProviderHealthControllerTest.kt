package me.rgunny.kachi.collector.adapter.`in`.web

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.adapter.`in`.web.response.ApiResponse
import me.rgunny.kachi.collector.application.port.out.CollectedArticle
import me.rgunny.kachi.collector.application.port.out.NewsProviderPort
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.NewsSource
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NewsProviderHealthController")
class NewsProviderHealthControllerTest {

    @Test
    @DisplayName("뉴스 provider를 실제 호출하고 샘플 응답을 반환한다")
    fun checkNewsProvider() = runBlocking {
        val googleProvider = FakeNewsProviderPort(
            source = NewsSource.GOOGLE,
            articles = listOf(
                article("NVIDIA 실적 발표", "https://kachi.com/news/1"),
                article("NVIDIA 신제품 공개", "https://kachi.com/news/2")
            )
        )
        val controller = NewsProviderHealthController(listOf(googleProvider))

        val response = controller.checkNewsProvider(source = "google", keyword = " NVIDIA ")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(CollectedKeyword.of("NVIDIA"), googleProvider.keyword)

        val body = response.body as ApiResponse<*>
        val data = body.data as NewsProviderHealthResponse
        assertEquals(true, body.success)
        assertEquals("GOOGLE", data.source)
        assertEquals("NVIDIA", data.keyword)
        assertEquals(2, data.fetchedCount)
        assertEquals("NVIDIA 실적 발표", data.samples.first().title)
    }

    @Test
    @DisplayName("지원하지 않는 source이면 400 응답을 반환한다")
    fun returnBadRequestWhenSourceIsInvalid() = runBlocking {
        val controller = NewsProviderHealthController(emptyList())

        val response = controller.checkNewsProvider(source = "unknown", keyword = "NVIDIA")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = response.body as ApiResponse<*>
        assertEquals(false, body.success)
        assertEquals("INVALID_NEWS_SOURCE", body.error?.code)
    }

    @Test
    @DisplayName("source는 유효하지만 활성화된 provider가 없으면 404 응답을 반환한다")
    fun returnNotFoundWhenProviderIsNotEnabled() = runBlocking {
        val controller = NewsProviderHealthController(emptyList())

        val response = controller.checkNewsProvider(source = "GOOGLE", keyword = "NVIDIA")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val body = response.body as ApiResponse<*>
        assertEquals(false, body.success)
        assertEquals("NEWS_PROVIDER_NOT_ENABLED", body.error?.code)
    }

    private class FakeNewsProviderPort(
        override val source: NewsSource,
        private val articles: List<CollectedArticle>
    ) : NewsProviderPort {
        var keyword: CollectedKeyword? = null

        override suspend fun collect(keyword: CollectedKeyword): List<CollectedArticle> {
            this.keyword = keyword
            return articles
        }
    }

    private fun article(title: String, url: String): CollectedArticle {
        return CollectedArticle(
            source = NewsSource.GOOGLE,
            title = title,
            url = url,
            publishedAt = Instant.parse("2026-05-30T00:00:00Z")
        )
    }
}
