package me.rgunny.kachi.collector.adapter.out.client.naver

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "kachi.collector.providers.naver")
data class NaverNewsProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "https://openapi.naver.com",
    val newsSearchPath: String = "/v1/search/news.json",
    val clientId: String = "",
    val clientSecret: String = "",
    val display: Int = 100,
    val start: Int = 1,
    val sort: String = "date",
    val connectTimeout: Duration = Duration.ofSeconds(2),
    val responseTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(5),
    val writeTimeout: Duration = Duration.ofSeconds(5),
    val maxInMemorySize: Int = 512 * 1024
)
