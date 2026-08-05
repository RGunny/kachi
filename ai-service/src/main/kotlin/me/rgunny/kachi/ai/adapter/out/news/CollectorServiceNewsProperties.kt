package me.rgunny.kachi.ai.adapter.out.news

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.ai.clients.collector-service")
data class CollectorServiceNewsProperties(
    val baseUrl: String,
    val newsPath: String,
    val timeout: Duration,
    val maxInMemorySize: Int,
)
