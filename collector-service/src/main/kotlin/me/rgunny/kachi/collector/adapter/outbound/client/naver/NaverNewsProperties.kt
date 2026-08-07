package me.rgunny.kachi.collector.adapter.outbound.client.naver

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.naver")
data class NaverNewsProperties(
    val enabled: Boolean,
    val baseUrl: String,
    val newsSearchPath: String,
    val clientId: String,
    val clientSecret: String,
    val display: Int,
    val start: Int,
    val sort: String,
    val connectTimeout: Duration,
    val responseTimeout: Duration,
    val readTimeout: Duration,
    val writeTimeout: Duration,
    val maxInMemorySize: Int,
)
