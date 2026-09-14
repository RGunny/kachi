package me.rgunny.kachi.story.application.service.merge

import java.time.Duration

/**
 * 병합 스캔의 대상 창과 한 번에 보는 story 수의 한도.
 *
 * [scanWindow]는 story를 연 시각부터 스캔 대상에서 빠지기까지의 시간이다.
 */
data class StoryMergePolicy(
    val scanWindow: Duration,
    val scanLimit: Int
) {
    init {
        require(!scanWindow.isNegative && !scanWindow.isZero) { "scan-window는 양수여야 합니다: $scanWindow" }
        require(scanLimit >= 1) { "scan-limit은 1 이상이어야 합니다: $scanLimit" }
    }
}
