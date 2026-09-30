package me.rgunny.kachi.collector.adapter.inbound.scheduler

import java.time.Duration

/**
 * 뉴스 수집 scheduler가 보는 실행 설정.
 *
 * `kachi.collector.scheduler.news`
 */
data class NewsCollectionSchedulerSettings(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration
)
