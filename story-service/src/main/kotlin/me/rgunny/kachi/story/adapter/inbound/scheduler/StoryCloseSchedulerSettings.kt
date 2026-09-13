package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration

/**
 * 닫기 scheduler가 보는 실행 설정.
 *
 * 값은 `kachi.story.jobs.close`에서 온다.
 */
data class StoryCloseSchedulerSettings(
    val enabled: Boolean,
    val interval: Duration,
    val initialDelay: Duration,
    val closeAfter: Duration,
    val batchLimit: Int
)
