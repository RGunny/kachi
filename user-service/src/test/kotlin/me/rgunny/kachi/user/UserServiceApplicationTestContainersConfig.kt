package me.rgunny.kachi.user

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class UserServiceApplicationTestContainersConfig {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer {
        return MySQLContainer("mysql:8.0")
            .withDatabaseName("kachi_test")
            .withUsername("rgunny")
            .withPassword("rgunny")
    }

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> {
        return GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
    }
}
