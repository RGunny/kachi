package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DisplayName("NewsTitle")
class NewsTitleTest {

    @Nested
    @DisplayName("of()")
    inner class Of {

        @Test
        @DisplayName("뉴스 제목은 앞뒤 공백을 제거한다")
        fun trimTitle() {
            val title = NewsTitle.of("  NVIDIA 실적 발표  ")

            assertEquals("NVIDIA 실적 발표", title.value)
        }

        @Test
        @DisplayName("뉴스 제목은 빈 값일 수 없다")
        fun rejectBlankTitle() {
            assertFailsWith<IllegalArgumentException> {
                NewsTitle.of("   ")
            }
        }

        @Test
        @DisplayName("대소문자와 연속 공백 차이는 같은 fingerprint를 만든다")
        fun createStableFingerprint() {
            val first = NewsTitle.of("NVIDIA   Earnings")
            val second = NewsTitle.of("nvidia earnings")

            assertEquals(first.fingerprint, second.fingerprint)
        }
    }
}
