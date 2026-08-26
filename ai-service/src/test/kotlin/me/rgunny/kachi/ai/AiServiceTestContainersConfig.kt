package me.rgunny.kachi.ai

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class AiServiceTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer {
        // transaction은 replica set에서만 동작한다. 기본값인 standalone으로 두면 outbox 저장 경계가 서지 않는다.
        return MongoDBContainer("mongo:7.0").withReplicaSet()
    }
}
