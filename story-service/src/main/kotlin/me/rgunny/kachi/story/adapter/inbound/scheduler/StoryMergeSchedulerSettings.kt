package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration

/**
 * 병합 scheduler가 보는 실행 설정.
 *
 * - `kachi.story.jobs.merge`
 */
data class StoryMergeSchedulerSettings(
    val enabled: Boolean,
    val interval: Duration,
    val initialDelay: Duration,
    val scanWindow: Duration,
    val scanLimit: Int
)
