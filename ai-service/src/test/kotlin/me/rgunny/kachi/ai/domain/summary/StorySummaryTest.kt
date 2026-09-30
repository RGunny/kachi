package me.rgunny.kachi.ai.domain.summary

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("StorySummary")
class StorySummaryTest {

    @Test
    @DisplayName("버전과 반영 기사, 누적 수를 담아 만든다")
    fun createSummary() {
        val summary = AiTestFixture.storySummary(version = 2, sourceNewsCount = 5)

        assertEquals(2, summary.version)
        assertEquals(5, summary.sourceNewsCount)
        assertEquals(listOf(AiTestFixture.NEWS_ID), summary.newNewsIds)
    }

    @ParameterizedTest
    @EnumSource(value = StoryDevelopmentKind::class, mode = EnumSource.Mode.EXCLUDE, names = ["DEVELOPMENT"])
    @DisplayName("첫 버전은 DEVELOPMENT만 허용한다")
    fun firstVersionMustBeDevelopment(kind: StoryDevelopmentKind) {
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storySummary(version = 1, developmentKind = kind)
        }
    }

    @ParameterizedTest
    @EnumSource(StoryDevelopmentKind::class)
    @DisplayName("둘째 버전부터는 어느 전개든 허용한다")
    fun laterVersionsAllowAnyKind(kind: StoryDevelopmentKind) {
        assertEquals(kind, AiTestFixture.storySummary(version = 2, developmentKind = kind).developmentKind)
    }

    @Test
    @DisplayName("누적 기사 수는 새 기사 수보다 작을 수 없다")
    fun rejectSmallerSourceCount() {
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storySummary(sourceNewsCount = 0)
        }
    }

    @Test
    @DisplayName("새 기사 없는 버전은 만들 수 없다")
    fun rejectEmptyNewNewsIds() {
        assertFailsWith<IllegalArgumentException> {
            AiTestFixture.storySummary(newNewsIds = emptyList(), sourceNewsCount = 1)
        }
    }
}
