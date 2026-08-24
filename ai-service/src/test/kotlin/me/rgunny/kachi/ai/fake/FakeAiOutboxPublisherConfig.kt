package me.rgunny.kachi.ai.fake

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * relay를 켠 컨텍스트에 발행 어댑터 자리를 채워 넣는다.
 *
 * 실제 broker 어댑터는 아직 없고, 배선 검증에 필요한 것은 relay가 발행 포트를 통해 호출된다는 사실뿐이다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeAiOutboxPublisherConfig {

    @Bean
    fun fakeAiOutboxPublisherPort(): FakeAiOutboxPublisherPort {
        return FakeAiOutboxPublisherPort()
    }
}
