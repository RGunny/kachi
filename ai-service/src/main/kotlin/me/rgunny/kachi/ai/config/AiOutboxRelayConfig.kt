package me.rgunny.kachi.ai.config

import me.rgunny.kachi.ai.application.port.inbound.outbox.RelayAiOutboxUseCase
import me.rgunny.kachi.ai.application.port.outbound.outbox.AiOutboxPublisherPort
import me.rgunny.kachi.ai.application.port.outbound.persistence.AiOutboxPersistencePort
import me.rgunny.kachi.ai.application.service.outbox.AiOutboxRelayPolicy
import me.rgunny.kachi.ai.application.service.outbox.RelayAiOutboxService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * relay가 켜져 있을 때만 relay 유스케이스와 실행 정책을 조립한다.
 *
 * 유스케이스는 발행 포트 구현이 있어야 만들 수 있고, 그 구현은 broker 어댑터가 붙은 뒤에야 생긴다.
 * 켜고 끄는 조건을 여기 한 곳에 두어 application 계층이 설정 키를 알 필요가 없게 한다.
 */
@Configuration
@ConditionalOnProperty(
    prefix = AiOutboxRelayProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class AiOutboxRelayConfig {

    @Bean
    fun aiOutboxRelayPolicy(properties: AiOutboxRelayProperties): AiOutboxRelayPolicy {
        return properties.toPolicy()
    }

    @Bean
    fun relayAiOutboxUseCase(
        outboxPersistencePort: AiOutboxPersistencePort,
        publisherPort: AiOutboxPublisherPort,
        policy: AiOutboxRelayPolicy,
        clock: Clock
    ): RelayAiOutboxUseCase {
        return RelayAiOutboxService(
            outboxPersistencePort = outboxPersistencePort,
            publisherPort = publisherPort,
            policy = policy,
            clock = clock
        )
    }
}
