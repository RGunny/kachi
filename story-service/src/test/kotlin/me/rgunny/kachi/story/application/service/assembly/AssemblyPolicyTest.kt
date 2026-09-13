package me.rgunny.kachi.story.application.service.assembly

import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.fixture.StoryTestFixture.assemblyPolicy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("AssemblyPolicy")
class AssemblyPolicyTest {

    @Test
    @DisplayName("θ_low는 0과 1 사이이고 θ_high는 θ_low 이상 1 이하여야 한다")
    fun requireOrderedThresholds() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaLow = -0.1) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaLow = 1.1, thetaHigh = 1.1) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaHigh = 0.59, thetaLow = 0.60) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaHigh = 1.01) }
        assertEquals(0.60, assemblyPolicy(thetaHigh = 0.60, thetaLow = 0.60).thetaHigh)
    }

    @Test
    @DisplayName("θ_judge는 0과 1 사이여야 한다")
    fun requireJudgeThresholdInUnitRange() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaJudge = -0.01) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(thetaJudge = 1.01) }
    }

    @Test
    @DisplayName("후보 수와 최근 기사 수는 1 이상이어야 한다")
    fun requirePositiveLimits() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(candidateLimit = 0) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(recentArticles = 0) }
    }

    @Test
    @DisplayName("후보 창은 양수여야 한다")
    fun requirePositiveWindow() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(candidateWindow = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(candidateWindow = Duration.ofHours(-1)) }
    }

    @Test
    @DisplayName("story 기사 상한은 2 이상이어야 한다")
    fun requireMaxArticlesAtLeastTwo() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(maxArticles = 1) }
        assertEquals(2, assemblyPolicy(maxArticles = 2).maxArticles)
    }

    @Test
    @DisplayName("경합 재시도 횟수는 0 이상이어야 한다")
    fun allowZeroCasRetries() {
        assertFailsWith<IllegalArgumentException> { assemblyPolicy(maxCasRetries = -1) }
        assertEquals(0, assemblyPolicy(maxCasRetries = 0).maxCasRetries)
    }
}
