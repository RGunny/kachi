package me.rgunny.kachi.user

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Import(UserServiceApplicationTestContainersConfig::class)
@DisplayName("UserServiceApplication 통합 테스트")
class UserServiceApplicationIntegrationTest {

    @Test
    @DisplayName("MySQL과 Redis 테스트 컨테이너로 애플리케이션 컨텍스트를 로드한다")
    fun loadApplicationContext() {
        // SpringBootTest context load 자체가 검증 대상이다.
    }

    companion object {
        private const val REDIS_PORT = 6379

        private val redisContainer = GenericContainer("redis:7-alpine")
            .withExposedPorts(REDIS_PORT)

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            redisContainer.start()
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(REDIS_PORT) }
        }

        @JvmStatic
        @AfterAll
        fun stopRedis() {
            redisContainer.stop()
        }
    }
}
