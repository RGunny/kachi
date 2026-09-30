package me.rgunny.kachi.ai.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.ai.clients.user-service")
data class UserServiceKeywordProperties(
    val baseUrl: String,
    val activeKeywordsPath: String,
    val timeout: Duration,
    val maxInMemorySize: Int,
)
