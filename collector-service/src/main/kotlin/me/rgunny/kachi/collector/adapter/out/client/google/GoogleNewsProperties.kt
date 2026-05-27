package me.rgunny.kachi.collector.adapter.out.client.google

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.google")
data class GoogleNewsProperties(
    val enabled: Boolean = true,
    val baseUrl: String = "https://news.google.com",
    val rssSearchPath: String = "/rss/search",
    val languageCode: String = "ko",
    val countryCode: String = "KR",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val responseTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(5),
    val writeTimeout: Duration = Duration.ofSeconds(5),
    val maxInMemorySize: Int = 512 * 1024
)
