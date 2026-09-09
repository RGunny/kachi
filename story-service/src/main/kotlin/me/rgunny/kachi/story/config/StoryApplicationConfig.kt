package me.rgunny.kachi.story.config

import java.time.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * story-service 공통 빈.
 */
@Configuration
class StoryApplicationConfig {

    /**
     * 시간 생성 기준을 application service 밖에서 주입한다.
     */
    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }
}
