package me.rgunny.kachi.ai.domain.outbox

import me.rgunny.kachi.ai.fixture.AiTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@DisplayName("AiOutboxClaim")
class AiOutboxClaimTest {
    private val now = AiTestFixture.NOW

    @Test
    @DisplayName("소유자가 비어 있으면 claim을 만들 수 없다")
    fun rejectBlankClaimedBy() {
        assertFailsWith<IllegalArgumentException> {
            AiOutboxClaim(claimedBy = " ", claimedAt = now)
        }
    }
}
