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
            assertEquals(
                setOf(news.urlHash),
                adapter.findExistingUrlHashes(NewsSource.GOOGLE, setOf(news.urlHash))
            )
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
        @DisplayName("같은 source에 저장된 URL hash만 반환한다")
        fun findExistingUrlHashes() = runBlocking {
            val saved = news(url = "https://kachi.com/news/1")
            val notSaved = news(url = "https://kachi.com/news/2")
            adapter.save(saved)

            val found = adapter.findExistingUrlHashes(
                source = NewsSource.GOOGLE,
                setOf(saved.urlHash, notSaved.urlHash)
            )

            assertEquals(setOf(saved.urlHash), found)
        }

        @Test
        @DisplayName("다른 source의 같은 URL hash는 반환하지 않는다")
        fun doesNotFindSameUrlHashWhenSourceIsDifferent() = runBlocking {
            val saved = news(url = "https://kachi.com/news/1", source = NewsSource.NAVER)
            adapter.save(saved)

            val found = adapter.findExistingUrlHashes(
                source = NewsSource.GOOGLE,
                urlHashes = setOf(saved.urlHash)
            )

            assertEquals(emptySet(), found)
        }

        @Test
        @DisplayName("조회 대상 URL hash가 비어 있으면 빈 Set을 반환한다")
        fun returnEmptySetWhenUrlHashesAreEmpty() = runBlocking {
            val found = adapter.findExistingUrlHashes(NewsSource.GOOGLE, emptySet())

            assertEquals(emptySet(), found)
        }
    }

    @Nested
    @DisplayName("findByKeyword()")
    inner class FindByKeyword {

        @Test
        @DisplayName("키워드가 매칭된 뉴스를 수집 시각 내림차순으로 조회한다")
        fun findByKeyword() = runBlocking {
            val older = news(
                url = "https://kachi.com/news/1",
                title = "NVIDIA 이전 뉴스",
                collectedAt = Instant.parse("2026-06-01T00:00:00Z")
            )
            val newer = news(
                url = "https://kachi.com/news/2",
                title = "NVIDIA 최신 뉴스",
                collectedAt = Instant.parse("2026-06-02T00:00:00Z")
            )
            val otherKeyword = news(
                url = "https://kachi.com/news/3",
                title = "TESLA 뉴스",
                keyword = CollectedKeyword.of("TESLA"),
                collectedAt = Instant.parse("2026-06-02T01:00:00Z")
            )
            adapter.save(older)
            adapter.save(newer)
            adapter.save(otherKeyword)

            val found = adapter.findByKeyword(
                keyword = keyword,
                from = Instant.parse("2026-06-01T00:00:00Z"),
                to = Instant.parse("2026-06-02T00:00:00Z"),
                limit = 20
            )

            assertEquals(listOf("NVIDIA 최신 뉴스", "NVIDIA 이전 뉴스"), found.map { it.title.value })
        }

        @Test
        @DisplayName("limit만큼만 조회한다")
        fun limitResults() = runBlocking {
            adapter.save(news(url = "https://kachi.com/news/1"))
            adapter.save(news(url = "https://kachi.com/news/2"))

            val found = adapter.findByKeyword(
                keyword = keyword,
                from = null,
                to = null,
                limit = 1
            )

            assertEquals(1, found.size)
        }
    }

    private fun news(
        url: String,
        source: NewsSource = NewsSource.GOOGLE,
        title: String = "NVIDIA 뉴스",
        keyword: CollectedKeyword = this.keyword,
        collectedAt: Instant = this.collectedAt
    ): News {
        return News.create(
            source = source,
            title = NewsTitle.of(title),
            url = NewsUrl.of(url),
            publishedAt = publishedAt,
            collectedAt = collectedAt,
            matchedKeywords = listOf(keyword)
        )
    }
}
