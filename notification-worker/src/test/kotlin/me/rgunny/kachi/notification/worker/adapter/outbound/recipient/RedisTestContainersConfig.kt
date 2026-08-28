package me.rgunny.kachi.notification.worker.adapter.outbound.recipient

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Redis adapter 통합 테스트용 Testcontainers 구성.
 */
@TestConfiguration(proxyBeanMethods = false)
class RedisTestContainersConfig {

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
    }
}
