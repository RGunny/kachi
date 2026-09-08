package me.rgunny.kachi.collector.domain.outbox

import me.rgunny.kachi.collector.fixture.CollectorTestFixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

@DisplayName("CollectorOutboxClaim")
class CollectorOutboxClaimTest {
    private val now = CollectorTestFixture.NOW

    @Test
    @DisplayName("소유자가 비어 있으면 claim을 만들 수 없다")
    fun rejectBlankClaimedBy() {
        assertFailsWith<IllegalArgumentException> {
            CollectorOutboxClaim(claimedBy = " ", claimedAt = now)
        }
    }
}
