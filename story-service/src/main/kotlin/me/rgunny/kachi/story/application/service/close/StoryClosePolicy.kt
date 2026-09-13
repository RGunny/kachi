package me.rgunny.kachi.story.application.service.close

import java.time.Duration

/**
 * 조용한 story를 닫는 기준과 한 번에 닫는 한도.
 *
 * [closeAfter]는 마지막 기사 발행 시각부터 닫기까지의 시간이다.
 */
data class StoryClosePolicy(
    val closeAfter: Duration,
    val batchLimit: Int
) {
    init {
        require(!closeAfter.isNegative && !closeAfter.isZero) { "close-after는 양수여야 합니다: $closeAfter" }
        require(batchLimit >= 1) { "batch-limit은 1 이상이어야 합니다: $batchLimit" }
    }
}
