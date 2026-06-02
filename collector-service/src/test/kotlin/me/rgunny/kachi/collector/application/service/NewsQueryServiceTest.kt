package me.rgunny.kachi.collector.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.application.port.`in`.ListNewsQuery
import me.rgunny.kachi.collector.application.port.out.NewsPersistencePort
import me.rgunny.kachi.collector.application.port.out.SaveNewsResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NewsQueryService")
class NewsQueryServiceTest {

    @Test
    @DisplayName("키워드와 기간으로 저장 뉴스를 조회한다")
    fun listNews() = runBlocking {
        val keyword = CollectedKeyword.of("NVIDIA")
        val persistence = FakeNewsPersistencePort(
            news = listOf(news("NVIDIA 실적 발표", "https://kachi.com/news/1", keyword))
        )
        val service = NewsQueryService(persistence)

        val result = service.listNews(
            ListNewsQuery(
                keyword = keyword,
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-06-02T00:00:00Z"),
                limit = 20
            )
        )

        assertEquals(keyword, persistence.keyword)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), persistence.from)
        assertEquals(Instant.parse("2026-06-02T00:00:00Z"), persistence.to)
        assertEquals(20, persistence.limit)
        assertEquals(1, result.size)
        assertEquals("GOOGLE", result.first().source)
        assertEquals("NVIDIA 실적 발표", result.first().title)
        assertEquals(listOf("NVIDIA"), result.first().matchedKeywords)
    }

    private class FakeNewsPersistencePort(
        private val news: List<News>
    ) : NewsPersistencePort {
        var keyword: CollectedKeyword? = null
        var from: Instant? = null
        var to: Instant? = null
        var limit: Int? = null

        override suspend fun findExistingUrlHashes(source: NewsSource, urlHashes: Set<String>): Set<String> {
            return emptySet()
        }

        override suspend fun findByKeyword(
            keyword: CollectedKeyword,
            from: Instant?,
            to: Instant?,
            limit: Int
        ): List<News> {
            this.keyword = keyword
            this.from = from
            this.to = to
            this.limit = limit
            return news
        }

        override suspend fun save(news: News): SaveNewsResult {
            return SaveNewsResult.SAVED
        }
    }

    private fun news(title: String, url: String, keyword: CollectedKeyword): News {
        return News.create(
            source = NewsSource.GOOGLE,
            title = NewsTitle.of(title),
            url = NewsUrl.of(url),
            publishedAt = Instant.parse("2026-06-01T10:00:00Z"),
            collectedAt = Instant.parse("2026-06-01T10:05:00Z"),
            matchedKeywords = listOf(keyword)
        )
    }
}
