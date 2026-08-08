package me.rgunny.kachi.collector.domain

import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@DisplayName("News")
class NewsTest {
    private val title = NewsTitle.of("NVIDIA 실적 발표")
    private val url = NewsUrl.of("https://kachi.com/news/1")
    private val publishedAt = CollectorTestFixture.NOW
    private val collectedAt = publishedAt.plusSeconds(60)
    private val keyword = CollectedKeyword.of("NVIDIA")

    @Nested
    @DisplayName("create()")
    inner class Create {

        @Test
        @DisplayName("뉴스를 생성한다")
        fun createNews() {
            val news = createNewsFixture()

            assertNotNull(news.id.value)
            assertEquals(NewsSource.GOOGLE, news.source)
            assertEquals(title, news.title)
            assertEquals(url, news.url)
            assertEquals(url.hash, news.urlHash)
            assertEquals(title.fingerprint, news.titleFingerprint)
            assertEquals(publishedAt, news.publishedAt)
            assertEquals(collectedAt, news.collectedAt)
            assertEquals(listOf(keyword), news.matchedKeywords)
        }

        @Test
        @DisplayName("중복 키워드는 제거한다")
        fun removeDuplicatedKeywords() {
            val news = News.create(
                source = NewsSource.GOOGLE,
                title = title,
                url = url,
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = listOf(keyword, keyword)
            )

            assertEquals(listOf(keyword), news.matchedKeywords)
        }

        @Test
        @DisplayName("매칭 키워드는 하나 이상이어야 한다")
        fun rejectEmptyMatchedKeywords() {
            assertFailsWith<IllegalArgumentException> {
                News.create(
                    source = NewsSource.GOOGLE,
                    title = title,
                    url = url,
                    publishedAt = publishedAt,
                    collectedAt = collectedAt,
                    matchedKeywords = emptyList()
                )
            }
        }
    }

    private fun createNewsFixture(): News {
        return News.create(
            source = NewsSource.GOOGLE,
            title = title,
            url = url,
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = listOf(keyword)
        )
    }

    @Nested
    @DisplayName("restore()")
    inner class Restore {

        @Test
        @DisplayName("뉴스를 저장된 상태로 복원한다")
        fun restoreNews() {
            val id = NewsId.of(UUID.randomUUID())

            val news = News.restore(
                id = id,
                source = NewsSource.NAVER,
                title = title,
                url = url,
                urlHash = "url-hash",
                titleFingerprint = "title-fingerprint",
                publishedAt = publishedAt,
                collectedAt = collectedAt,
                matchedKeywords = listOf(keyword)
            )

            assertEquals(id, news.id)
            assertEquals(NewsSource.NAVER, news.source)
            assertEquals("url-hash", news.urlHash)
            assertEquals("title-fingerprint", news.titleFingerprint)
        }
    }
}
