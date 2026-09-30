package me.rgunny.kachi.notification.worker.config

import me.rgunny.kachi.notification.application.port.outbound.recipient.RecipientResolverPort
import me.rgunny.kachi.notification.worker.adapter.monitoring.NotificationWorkerMetrics
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.CachedRecipientResolver
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.RecipientAddressCache
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.RedisRecipientAddressCache
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.ResolvedRecipientCacheCodec
import me.rgunny.kachi.notification.worker.adapter.outbound.recipient.UserServiceRecipientResolver
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper

/**
 * 수신 주소 resolver 조립.
 * user-service 클라이언트를 Redis 캐시로 감싸 dispatch use case에 준다.
 */
@Configuration
@EnableConfigurationProperties(NotificationRecipientProperties::class)
class NotificationRecipientConfig {

    /**
     * user-service 채널 바인딩 조회 전용 WebClient. 타임아웃은 호출 쪽 `.timeout` 연산자가 건다.
     */
    @Bean(RECIPIENT_WEB_CLIENT)
    fun recipientWebClient(properties: NotificationRecipientProperties): WebClient {
        return WebClient.builder()
            .baseUrl(properties.userService.baseUrl)
            .codecs { it.defaultCodecs().maxInMemorySize(properties.userService.maxInMemorySize) }
            .build()
    }

    @Bean
    fun recipientAddressCache(
        redisTemplate: ReactiveStringRedisTemplate,
        jsonMapper: JsonMapper,
    ): RecipientAddressCache {
        return RedisRecipientAddressCache(
            redisTemplate = redisTemplate,
            codec = ResolvedRecipientCacheCodec(jsonMapper),
        )
    }

    @Bean
    fun recipientResolverPort(
        @Qualifier(RECIPIENT_WEB_CLIENT) webClient: WebClient,
        recipientAddressCache: RecipientAddressCache,
        properties: NotificationRecipientProperties,
        metrics: NotificationWorkerMetrics,
    ): RecipientResolverPort {
        return CachedRecipientResolver(
            delegate = UserServiceRecipientResolver(
                webClient = webClient,
                channelBindingPath = properties.userService.channelBindingPath,
                timeout = properties.userService.timeout,
            ),
            cache = recipientAddressCache,
            ttl = properties.cacheTtl,
            metrics = metrics,
        )
    }

    companion object {
        const val RECIPIENT_WEB_CLIENT = "recipientWebClient"
    }
}
