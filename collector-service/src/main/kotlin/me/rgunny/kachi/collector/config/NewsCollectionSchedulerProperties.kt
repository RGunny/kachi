package me.rgunny.kachi.collector.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.scheduler.news")
data class NewsCollectionSchedulerProperties(
    val enabled: Boolean,
    val fixedDelay: Duration,
    val initialDelay: Duration,
)
