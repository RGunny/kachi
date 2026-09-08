package me.rgunny.kachi.collector.application.service

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.application.port.inbound.news.model.ListNewsQuery
import me.rgunny.kachi.collector.application.port.outbound.news.NewsPersistencePort
import me.rgunny.kachi.collector.domain.outbox.CollectorOutbox
import me.rgunny.kachi.collector.application.port.outbound.news.model.SaveNewsResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsExcerpt
import me.rgunny.kachi.collector.domain.NewsLanguage
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NewsQueryService")
class NewsQueryServiceTest {

    private val from = CollectorTestFixture.NOW
    private val to = from.plus(Duration.ofDays(1))

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
                from = from,
                to = to,
                limit = 20
            )
        )

        assertEquals(keyword, persistence.keyword)
        assertEquals(from, persistence.from)
        assertEquals(to, persistence.to)
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

        override suspend fun save(news: News, outbox: CollectorOutbox): SaveNewsResult {
            return SaveNewsResult.SAVED
        }
    }

    private fun news(title: String, url: String, keyword: CollectedKeyword): News {
        return News.create(
            source = NewsSource.GOOGLE,
            title = NewsTitle.of(title),
            excerpt = NewsExcerpt.of("$title 발췌문"),
            url = NewsUrl.of(url),
            language = NewsLanguage.of("ko"),
            publishedAt = from.plus(Duration.ofHours(10)),
            collectedAt = from.plus(Duration.ofHours(10)).plusSeconds(300),
            matchedKeywords = listOf(keyword)
        )
    }
}
