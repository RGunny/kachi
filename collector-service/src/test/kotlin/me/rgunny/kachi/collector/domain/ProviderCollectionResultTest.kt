package me.rgunny.kachi.collector.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@DisplayName("ProviderCollectionResult")
class ProviderCollectionResultTest {

    @Nested
    @DisplayName("success()")
    inner class Success {

        @Test
        @DisplayName("provider 성공 결과를 생성한다")
        fun createSuccessResult() {
            val result = ProviderCollectionResult.success(
                source = NewsSource.GOOGLE,
                fetchedCount = 10,
                savedCount = 6,
                duplicateCount = 4
            )

            assertEquals(NewsSource.GOOGLE, result.source)
            assertEquals(ProviderCollectionStatus.SUCCEEDED, result.status)
            assertEquals(10, result.fetchedCount)
            assertEquals(6, result.savedCount)
            assertEquals(4, result.duplicateCount)
            assertNull(result.failureMessage)
        }

        @Test
        @DisplayName("성공 건수는 음수일 수 없다")
        fun rejectNegativeCount() {
            assertFailsWith<IllegalArgumentException> {
                ProviderCollectionResult.success(
                    source = NewsSource.GOOGLE,
                    fetchedCount = -1,
                    savedCount = 0,
                    duplicateCount = 0
                )
            }
        }
    }

    @Nested
    @DisplayName("failure()")
    inner class Failure {

        @Test
        @DisplayName("provider 실패 결과를 생성한다")
        fun createFailureResult() {
            val result = ProviderCollectionResult.failure(
                source = NewsSource.NAVER,
                failureMessage = "timeout"
            )

            assertEquals(NewsSource.NAVER, result.source)
            assertEquals(ProviderCollectionStatus.FAILED, result.status)
            assertEquals("timeout", result.failureMessage)
        }

        @Test
        @DisplayName("실패 메시지는 빈 값일 수 없다")
        fun rejectBlankFailureMessage() {
            assertFailsWith<IllegalArgumentException> {
                ProviderCollectionResult.failure(
                    source = NewsSource.NAVER,
                    failureMessage = "   "
                )
            }
        }
    }
}
