package me.rgunny.kachi.ai.fake

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * relay를 켠 컨텍스트에 발행 어댑터 자리를 채워 넣는 테스트 설정.
 *
 * events를 끈 채 relay만 켠 컨텍스트에는 발행 포트 구현이 없어 기동에 실패한다. 이 fake가 그 자리를 채운다.
 * 배선 검증에 필요한 것은 relay가 발행 포트를 통해 호출된다는 사실뿐이라 실제 Kafka 어댑터는 넣지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class FakeAiOutboxPublisherConfig {

    @Bean
    fun fakeAiOutboxPublisherPort(): FakeAiOutboxPublisherPort {
        return FakeAiOutboxPublisherPort()
    }
}
