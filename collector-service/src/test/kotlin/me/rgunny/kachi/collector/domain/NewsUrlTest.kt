package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@DisplayName("NewsUrl")
class NewsUrlTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("URL은 앞뒤 공백을 제거하고 원문을 그대로 둔다")
        fun trimUrl() {
            val url = NewsUrl.of("  https://kachi.com/news/1?utm_source=x  ")

            assertEquals("https://kachi.com/news/1?utm_source=x", url.value)
        }

        @Test
        @DisplayName("URL은 빈 값일 수 없다")
        fun rejectBlankUrl() {
            assertFailsWith<IllegalArgumentException> {
                NewsUrl.of("   ")
            }
        }
    }

    @Nested
    @DisplayName("canonical")
    inner class Canonical {

        @Test
        @DisplayName("scheme과 host를 소문자로 만들고 기본 포트와 fragment를 뺀다")
        fun normalizeSchemeHostPortFragment() {
            val url = NewsUrl.of("HTTPS://Kachi.COM:443/news/1#comments")

            assertEquals("https://kachi.com/news/1", url.canonical)
        }

        @Test
        @DisplayName("기본이 아닌 포트는 남긴다")
        fun keepNonDefaultPort() {
            val url = NewsUrl.of("https://kachi.com:8443/news/1")

            assertEquals("https://kachi.com:8443/news/1", url.canonical)
        }

        @Test
        @DisplayName("추적 파라미터를 빼고 남은 파라미터를 정렬한다")
        fun dropTrackingParametersAndSortRest() {
            val url = NewsUrl.of("https://kachi.com/news?utm_source=x&id=1&fbclid=abc&page=2&UTM_medium=y&ref=home")

            assertEquals("https://kachi.com/news?id=1&page=2", url.canonical)
        }

        @Test
        @DisplayName("끝 슬래시와 AMP 경로를 뺀다")
        fun dropTrailingSlashAndAmpPath() {
            assertEquals("https://kachi.com/news/1", NewsUrl.of("https://kachi.com/news/1/").canonical)
            assertEquals("https://kachi.com/news/1", NewsUrl.of("https://kachi.com/news/1/amp").canonical)
            assertEquals("https://kachi.com/news/1", NewsUrl.of("https://kachi.com/news/1/amp/").canonical)
            assertEquals("https://kachi.com", NewsUrl.of("https://kachi.com/").canonical)
        }

        @Test
        @DisplayName("amp 서브도메인을 원본 호스트로 돌린다")
        fun dropAmpSubdomain() {
            assertEquals("https://kachi.com/news/1", NewsUrl.of("https://amp.kachi.com/news/1").canonical)
            assertEquals("https://amp.com/news/1", NewsUrl.of("https://amp.com/news/1").canonical)
        }

        @Test
        @DisplayName("파싱할 수 없는 URL은 원문을 그대로 쓴다")
        fun keepUnparseableUrl() {
            val url = NewsUrl.of("https://kachi.com/뉴스 1?a=b#x")

            assertEquals("https://kachi.com/뉴스 1?a=b#x", url.canonical)
        }
    }

    @Nested
    @DisplayName("hash")
    inner class Hash {

        @Test
        @DisplayName("같은 페이지의 URL 변형은 같은 hash를 만든다")
        fun createStableHashForVariants() {
            val first = NewsUrl.of("https://kachi.com/news/1?utm_source=naver&id=7#top")
            val second = NewsUrl.of(" HTTPS://KACHI.com/news/1/?id=7 ")

            assertEquals(first.hash, second.hash)
        }

        @Test
        @DisplayName("의미 있는 파라미터가 다르면 hash가 다르다")
        fun differentHashForDifferentContentParameters() {
            val first = NewsUrl.of("https://kachi.com/news?id=1")
            val second = NewsUrl.of("https://kachi.com/news?id=2")

            assertNotEquals(first.hash, second.hash)
        }
    }
}
