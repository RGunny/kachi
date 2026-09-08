package me.rgunny.kachi.collector.config

import me.rgunny.kachi.collector.application.port.inbound.outbox.RelayCollectorOutboxUseCase
import me.rgunny.kachi.collector.application.port.outbound.outbox.CollectorOutboxPublisherPort
import me.rgunny.kachi.collector.application.port.outbound.persistence.CollectorOutboxPersistencePort
import me.rgunny.kachi.collector.application.service.outbox.CollectorOutboxRelayPolicy
import me.rgunny.kachi.collector.application.service.outbox.RelayCollectorOutboxService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * relay가 켜져 있을 때만 relay 유스케이스와 실행 정책을 조립한다.
 *
 * 유스케이스는 발행 포트 구현이 있어야 만들 수 있고, 그 구현은 발행 어댑터가 켜져 있을 때만 생긴다.
 * relay는 켰는데 발행 어댑터가 없는 조합은 운영 모드가 아니라 잘못된 설정이다.
 * 조용히 보류하지 않고 기동에서 실패시켜 켜져 있다고 믿는데 아무것도 나가지 않는 상태를 막는다.
 * 켜고 끄는 조건을 여기 한 곳에 두어 application 계층이 설정 키를 알 필요가 없게 한다.
 */
@Configuration
@ConditionalOnProperty(
    prefix = CollectorOutboxRelayProperties.PREFIX,
    name = ["enabled"],
    havingValue = "true"
)
class CollectorOutboxRelayConfig {

    @Bean
    fun aiOutboxRelayPolicy(properties: CollectorOutboxRelayProperties): CollectorOutboxRelayPolicy {
        return properties.toPolicy()
    }

    @Bean
    fun relayCollectorOutboxUseCase(
        outboxPersistencePort: CollectorOutboxPersistencePort,
        publisherPort: ObjectProvider<CollectorOutboxPublisherPort>,
        policy: CollectorOutboxRelayPolicy,
        clock: Clock
    ): RelayCollectorOutboxUseCase {
        val publisher = checkNotNull(publisherPort.ifAvailable) { NO_PUBLISH_TARGET_MESSAGE }

        return RelayCollectorOutboxService(
            outboxPersistencePort = outboxPersistencePort,
            publisherPort = publisher,
            policy = policy,
            clock = clock
        )
    }

    companion object {
        const val NO_PUBLISH_TARGET_MESSAGE =
            "outbox relay가 켜져 있으나 발행 어댑터가 없습니다. " +
                "${CollectorEventsProperties.PREFIX}.enabled를 켜거나 ${CollectorOutboxRelayProperties.PREFIX}.enabled를 끄십시오"
    }
}
