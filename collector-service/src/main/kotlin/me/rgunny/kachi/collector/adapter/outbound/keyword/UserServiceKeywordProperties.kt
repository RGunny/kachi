package me.rgunny.kachi.collector.adapter.outbound.keyword

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.clients.user-service")
data class UserServiceKeywordProperties(
    val baseUrl: String,
    val activeKeywordsPath: String,
    val timeout: Duration,
    val maxInMemorySize: Int,
)
