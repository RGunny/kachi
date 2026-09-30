package me.rgunny.kachi.story.config

import me.rgunny.kachi.story.application.service.assembly.AssemblyPolicy
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 조립 규칙을 설정 값에서 조립하는 설정.
 */
@Configuration
@EnableConfigurationProperties(AssemblyProperties::class)
class AssemblyConfig {

    @Bean
    fun assemblyPolicy(properties: AssemblyProperties): AssemblyPolicy {
        return properties.toPolicy()
    }
}
