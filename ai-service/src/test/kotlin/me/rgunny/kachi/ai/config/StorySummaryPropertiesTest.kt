package me.rgunny.kachi.ai.config

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StorySummaryConfig")
class StorySummaryPropertiesTest {

    @Test
    @DisplayName("설정 값이 정책 객체로 옮겨진다")
    fun buildPolicyFromProperties() {
        val policy = StorySummaryConfig().storySummaryPolicy(
            AiTestFixture.storySummaryProperties(minNewArticles = 5, maxArticlesPerVersion = 10)
        )

        assertEquals(5, policy.minNewArticles)
        assertEquals(10, policy.maxArticlesPerVersion)
    }

    @Test
    @DisplayName("정책 검증에 걸리는 값은 조립에서 실패한다")
    fun failOnInvalidValues() {
        assertFailsWith<IllegalArgumentException> {
            StorySummaryConfig().storySummaryPolicy(AiTestFixture.storySummaryProperties(minNewArticles = 0))
        }
    }
}
