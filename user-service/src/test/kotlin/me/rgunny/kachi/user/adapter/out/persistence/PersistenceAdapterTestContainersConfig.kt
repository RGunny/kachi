package me.rgunny.kachi.user.adapter.out.persistence

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

@TestConfiguration(proxyBeanMethods = false)
class PersistenceAdapterTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer {
        return MySQLContainer("mysql:8.0")
            .withDatabaseName("kachi_test")
            .withUsername("rgunny")
            .withPassword("rgunny")
    }
}
