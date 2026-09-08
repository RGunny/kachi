package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewsExcerpt")
class NewsExcerptTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("연속 공백을 하나로 접고 앞뒤 공백을 지운다")
        fun normalizeWhitespace() {
            val excerpt = NewsExcerpt.of("  엔비디아가\n\n실적을   발표했다  ")

            assertEquals("엔비디아가 실적을 발표했다", excerpt.value)
        }

        @Test
        @DisplayName("공백뿐이면 거부한다")
        fun rejectBlank() {
            assertFailsWith<IllegalArgumentException> { NewsExcerpt.of("   ") }
            assertFailsWith<IllegalArgumentException> { NewsExcerpt.of("") }
        }

        @Test
        @DisplayName("상한을 넘는 부분은 잘라 낸다")
        fun truncateOverMaxLength() {
            val excerpt = NewsExcerpt.of("a".repeat(NewsExcerpt.MAX_LENGTH + 10))

            assertEquals(NewsExcerpt.MAX_LENGTH, excerpt.value.length)
        }
    }
}
