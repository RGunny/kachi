package me.rgunny.kachi.notification.worker.adapter.outbound.persistence.mongo

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mongodb.MongoDBContainer

@TestConfiguration(proxyBeanMethods = false)
class PersistenceAdapterTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mongoContainer(): MongoDBContainer {
        return MongoDBContainer("mongo:7.0")
    }
}
