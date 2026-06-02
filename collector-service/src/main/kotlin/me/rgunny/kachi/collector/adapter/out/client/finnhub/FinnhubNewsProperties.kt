package me.rgunny.kachi.collector.adapter.out.client.finnhub

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.finnhub")
data class FinnhubNewsProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://finnhub.io",
    val companyNewsPath: String = "/api/v1/company-news",
    val apiKey: String = "",
    val lookbackDays: Long = 7,
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val responseTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(5),
    val writeTimeout: Duration = Duration.ofSeconds(5),
    val maxInMemorySize: Int = 512 * 1024
)
