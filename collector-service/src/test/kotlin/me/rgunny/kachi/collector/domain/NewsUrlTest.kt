package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewsUrl")
class NewsUrlTest {

    @Nested
    @DisplayName("of()")
    inner class Of {
        
        @Test
        @DisplayName("URL은 앞뒤 공백을 제거한다")
        fun trimUrl() {
            val url = NewsUrl.of("  https://kachi.com/news/1  ")

            assertEquals("https://kachi.com/news/1", url.value)
        }

        @Test
        @DisplayName("URL은 빈 값일 수 없다")
        fun rejectBlankUrl() {
            assertFailsWith<IllegalArgumentException> {
                NewsUrl.of("   ")
            }
        }

        @Test
        @DisplayName("같은 URL은 같은 hash를 만든다")
        fun createStableHash() {
            val first = NewsUrl.of("https://kachi.com/news/1")
            val second = NewsUrl.of(" https://kachi.com/news/1 ")

            assertEquals(first.hash, second.hash)
        }
    }
}
