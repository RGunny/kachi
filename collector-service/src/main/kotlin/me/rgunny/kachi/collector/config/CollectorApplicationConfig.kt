package me.rgunny.kachi.collector.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class CollectorApplicationConfig {

    /**
     * 시간 생성 기준을 application service 밖에서 주입한다.
     */
    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }
}
