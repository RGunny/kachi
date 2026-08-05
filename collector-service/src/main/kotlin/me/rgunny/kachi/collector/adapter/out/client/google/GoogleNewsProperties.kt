package me.rgunny.kachi.collector.adapter.out.client.google

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.google")
data class GoogleNewsProperties(
    val enabled: Boolean,
    val baseUrl: String,
    val rssSearchPath: String,
    val languageCode: String,
    val countryCode: String,
    val connectTimeout: Duration,
    val responseTimeout: Duration,
    val readTimeout: Duration,
    val writeTimeout: Duration,
    val maxInMemorySize: Int,
)
