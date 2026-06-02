package me.rgunny.kachi.collector.adapter.`in`.scheduler

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.scheduler.news")
data class NewsCollectionSchedulerProperties(
    val enabled: Boolean = true,
    val fixedDelay: Duration = Duration.ofMinutes(10),
    val initialDelay: Duration = Duration.ofSeconds(30)
)
