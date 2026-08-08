package me.rgunny.kachi.collector.adapter.outbound.client.finnhub

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.finnhub")
data class FinnhubNewsProperties(
    val enabled: Boolean,
    val baseUrl: String,
    val companyNewsPath: String,
    val apiKey: String,
    val lookbackDays: Long,
    val connectTimeout: Duration,
    val responseTimeout: Duration,
    val readTimeout: Duration,
    val writeTimeout: Duration,
    val maxInMemorySize: Int,
)
