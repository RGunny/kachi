package me.rgunny.kachi.notification.routing

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer

/**
 * 전체 컨텍스트 통합 테스트가 쓰는 인프라 컨테이너.
 * 소비·발행 계약을 실제 broker 위에서 보기 위해 Kafka를 함께 띄운다.
 */
@TestConfiguration(proxyBeanMethods = false)
class RoutingTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer {
        return MongoDBContainer("mongo:7.0").withReplicaSet()
    }

    @Bean
    @ServiceConnection
    fun kafkaContainer(): KafkaContainer {
        return KafkaContainer("apache/kafka:3.9.1")
    }
}
