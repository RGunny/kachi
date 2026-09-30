package me.rgunny.kachi.story.application.service.close

import java.time.Duration
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("StoryClosePolicy")
class StoryClosePolicyTest {

    @Test
    @DisplayName("close-after가 0이거나 음수면 만들 수 없다")
    fun rejectNonPositiveCloseAfter() {
        assertFailsWith<IllegalArgumentException> {
            StoryClosePolicy(closeAfter = Duration.ZERO, batchLimit = 100)
        }
        assertFailsWith<IllegalArgumentException> {
            StoryClosePolicy(closeAfter = Duration.ofHours(-1), batchLimit = 100)
        }
    }

    @Test
    @DisplayName("batch-limit이 1 미만이면 만들 수 없다")
    fun rejectNonPositiveBatchLimit() {
        assertFailsWith<IllegalArgumentException> {
            StoryClosePolicy(closeAfter = Duration.ofHours(48), batchLimit = 0)
        }
    }
}
