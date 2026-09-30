package me.rgunny.kachi.story.domain.outbox

import kotlin.test.assertFailsWith
import me.rgunny.kachi.story.fixture.StoryTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryOutboxClaim")
class StoryOutboxClaimTest {
    private val now = StoryTestFixture.NOW

    @Test
    @DisplayName("소유자가 비어 있으면 claim을 만들 수 없다")
    fun rejectBlankClaimedBy() {
        assertFailsWith<IllegalArgumentException> {
            StoryOutboxClaim(claimedBy = " ", claimedAt = now)
        }
    }
}
