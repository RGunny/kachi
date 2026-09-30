package me.rgunny.kachi.story.adapter.inbound.scheduler

import java.time.Duration

/**
 * relay scheduler가 보는 실행 설정.
 *
 * `kachi.story.outbox.relay`
 */
data class StoryOutboxRelaySchedulerSettings(
    val publisherId: String,
    val fixedDelay: Duration,
    val initialDelay: Duration,
    val batchSize: Int,
    val publishingVisibilityTimeout: Duration
)
