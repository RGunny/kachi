package me.rgunny.kachi.collector

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@SpringBootTest
@Import(CollectorServiceTestContainersConfig::class)
class CollectorServiceApplicationTest {

    @Test
    fun contextLoads() {
    }
}
