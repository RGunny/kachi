package me.rgunny.kachi.collector.adapter.out.persistence

import kotlinx.coroutines.runBlocking
import me.rgunny.kachi.collector.application.port.out.SaveNewsResult
import me.rgunny.kachi.collector.domain.CollectedKeyword
import me.rgunny.kachi.collector.domain.News
import me.rgunny.kachi.collector.domain.NewsSource
import me.rgunny.kachi.collector.domain.NewsTitle
import me.rgunny.kachi.collector.domain.NewsUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import kotlin.test.assertEquals

@DisplayName("NewsPersistenceAdapter 통합 테스트")
class NewsPersistenceAdapterIntegrationTest : PersistenceAdapterIntegrationTest() {

    @Autowired
    private lateinit var adapter: NewsPersistenceAdapter

    @Autowired
    private lateinit var repository: NewsMongoRepository

    private val collectedAt = Instant.parse("2026-05-28T00:00:00Z")
    private val publishedAt = Instant.parse("2026-05-27T10:00:00Z")
    private val keyword = CollectedKeyword.of("NVIDIA")

    @BeforeEach
    fun cleanUp() {
        repository.deleteAll().block()
    }

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        @DisplayName("News 도메인을 MongoDB에 저장한다")
        fun saveNews() = runBlocking {
            val news = news(url = "https://kachi.com/news/1")

            val result = adapter.save(news)

            assertEquals(SaveNewsResult.SAVED, result)
            assertEquals(setOf(news.urlHash), adapter.findExistingUrlHashes(setOf(news.urlHash)))
        }

        @Test
        @DisplayName("같은 source와 URL hash는 중복으로 처리한다")
        fun returnDuplicatedWhenSourceAndUrlHashAlreadyExists() = runBlocking {
            val first = news(url = "https://kachi.com/news/1")
            val second = news(url = "https://kachi.com/news/1")

            val firstResult = adapter.save(first)
            val secondResult = adapter.save(second)

            assertEquals(SaveNewsResult.SAVED, firstResult)
            assertEquals(SaveNewsResult.DUPLICATED, secondResult)
        }
    }

    @Nested
    @DisplayName("findExistingUrlHashes()")
    inner class FindExistingUrlHashes {

        @Test
        @DisplayName("저장된 URL hash만 반환한다")
        fun findExistingUrlHashes() = runBlocking {
            val saved = news(url = "https://kachi.com/news/1")
            val notSaved = news(url = "https://kachi.com/news/2")
            adapter.save(saved)

            val found = adapter.findExistingUrlHashes(
                setOf(saved.urlHash, notSaved.urlHash)
            )

            assertEquals(setOf(saved.urlHash), found)
        }

        @Test
        @DisplayName("조회 대상 URL hash가 비어 있으면 빈 Set을 반환한다")
        fun returnEmptySetWhenUrlHashesAreEmpty() = runBlocking {
            val found = adapter.findExistingUrlHashes(emptySet())

            assertEquals(emptySet(), found)
        }
    }

    private fun news(url: String): News {
        return News.create(
            source = NewsSource.GOOGLE,
            title = NewsTitle.of("NVIDIA 뉴스"),
            url = NewsUrl.of(url),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = listOf(keyword)
        )
    }
}
