package me.rgunny.kachi.user.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.redis.core.script.RedisScript

@Configuration
class RedisScriptConfig {

    /**
     * refresh token rotation을 Redis 단일 명령처럼 원자적으로 실행하기 위한 Lua script.
     * Bean으로 등록하면 Spring Data Redis가 script SHA1을 재사용할 수 있다.
     */
    @Bean
    fun rotateRefreshTokenScript(): RedisScript<Long> {
        return RedisScript.of(
            ClassPathResource(ROTATE_REFRESH_TOKEN_SCRIPT_PATH),
            Long::class.java
        )
    }

    companion object {
        private const val ROTATE_REFRESH_TOKEN_SCRIPT_PATH = "scripts/rotate-refresh-token.lua"
    }
}
