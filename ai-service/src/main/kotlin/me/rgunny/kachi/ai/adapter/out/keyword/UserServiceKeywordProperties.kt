package me.rgunny.kachi.ai.adapter.out.keyword

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.ai.clients.user-service")
data class UserServiceKeywordProperties(
    val baseUrl: String,
    val activeKeywordsPath: String = "/api/v1/internal/keywords/active",
    val timeout: Duration = Duration.ofSeconds(3),
    val maxInMemorySize: Int = 256 * 1024
)
