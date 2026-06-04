package me.rgunny.kachi.ai.adapter.out.news

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.ai.clients.collector-service")
data class CollectorServiceNewsProperties(
    val baseUrl: String,
    val newsPath: String = "/api/v1/internal/news",
    val timeout: Duration = Duration.ofSeconds(3),
    val maxInMemorySize: Int = 256 * 1024
)
