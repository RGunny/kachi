package me.rgunny.kachi.collector

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer

/**
 * 전체 컨텍스트 통합 테스트가 쓰는 인프라 컨테이너.
 *
 * Kafka는 events가 꺼진 컨텍스트에서는 쓰이지 않지만, 같은 설정을 import하는 테스트들이 컨텍스트 캐시를 공유하도록 여기서 함께 띄운다.
 */
@TestConfiguration(proxyBeanMethods = false)
class CollectorServiceTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer {
        // transaction은 replica set에서만 동작한다. 기본값인 standalone으로 두면 기사·outbox 저장 경계가 서지 않는다.
        return MongoDBContainer("mongo:7.0").withReplicaSet()
    }

    @Bean
    @ServiceConnection
    fun kafkaContainer(): KafkaContainer {
        // 로컬 compose와 같은 이미지를 쓴다.
        return KafkaContainer("apache/kafka:3.9.1")
    }
}
