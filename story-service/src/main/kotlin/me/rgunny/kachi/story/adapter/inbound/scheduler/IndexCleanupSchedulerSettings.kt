package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration

/**
 * 색인 정리 scheduler가 보는 실행 설정.
 *
 * `kachi.story.jobs.cleanup`
 */
data class IndexCleanupSchedulerSettings(
    val enabled: Boolean,
    val interval: Duration,
    val initialDelay: Duration
)
